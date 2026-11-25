//! Minimal, zero-dependency AWS S3 SigV4 presigner & object helper.
//! Uses `hmac`, `sha2`, `hex`, `chrono`, and `reqwest` already in Cargo.toml.

use chrono::Utc;
use hmac::{Hmac, KeyInit, Mac};
use sha2::{Digest, Sha256};

use crate::config::S3Config;

type HmacSha256 = Hmac<Sha256>;

fn hmac_sha256(key: &[u8], data: &[u8]) -> Vec<u8> {
    let mut mac = HmacSha256::new_from_slice(key).expect("HMAC accepts any key length");
    mac.update(data);
    mac.finalize().into_bytes().to_vec()
}

fn sha256_hex(data: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(data);
    hex::encode(hasher.finalize())
}

fn uri_encode(input: &str, encode_slash: bool) -> String {
    let mut out = String::with_capacity(input.len());
    for b in input.bytes() {
        let keep = b.is_ascii_alphanumeric()
            || matches!(b, b'-' | b'_' | b'.' | b'~')
            || (!encode_slash && b == b'/');
        if keep {
            out.push(b as char);
        } else {
            out.push('%');
            out.push_str(&format!("{b:02X}"));
        }
    }
    out
}

fn signing_key(secret: &str, date_stamp: &str, region: &str) -> Vec<u8> {
    let k_date = hmac_sha256(format!("AWS4{secret}").as_bytes(), date_stamp.as_bytes());
    let k_region = hmac_sha256(&k_date, region.as_bytes());
    let k_service = hmac_sha256(&k_region, b"s3");
    hmac_sha256(&k_service, b"aws4_request")
}

impl S3Config {
    pub fn host(&self) -> String {
        format!("{}.s3.{}.amazonaws.com", self.bucket, self.region)
    }

    pub fn presign_put(&self, key: &str, expires_secs: u64) -> String {
        self.presign("PUT", key, expires_secs)
    }

    pub fn presign_get(&self, key: &str, expires_secs: u64) -> String {
        self.presign("GET", key, expires_secs)
    }

    fn presign(&self, method: &str, key: &str, expires_secs: u64) -> String {
        let now = Utc::now();
        let amz_date = now.format("%Y%m%dT%H%M%SZ").to_string();
        let date_stamp = now.format("%Y%m%d").to_string();
        let host = self.host();
        let encoded_key = uri_encode(key.trim_start_matches('/'), false);
        let credential = format!("{}/{date_stamp}/{}/s3/aws4_request", self.access_key_id, self.region);

        let query = [
            ("X-Amz-Algorithm", "AWS4-HMAC-SHA256".to_string()),
            ("X-Amz-Credential", uri_encode(&credential, true)),
            ("X-Amz-Date", amz_date.clone()),
            ("X-Amz-Expires", expires_secs.to_string()),
            ("X-Amz-SignedHeaders", "host".to_string()),
        ];
        let canonical_query: String = query
            .iter()
            .map(|(k, v)| format!("{k}={v}"))
            .collect::<Vec<_>>()
            .join("&");

        let canonical_request = format!(
            "{method}\n/{encoded_key}\n{canonical_query}\nhost:{host}\n\nhost\nUNSIGNED-PAYLOAD"
        );
        let scope = format!("{date_stamp}/{}/s3/aws4_request", self.region);
        let string_to_sign = format!(
            "AWS4-HMAC-SHA256\n{amz_date}\n{scope}\n{}",
            sha256_hex(canonical_request.as_bytes())
        );

        let sig_key = signing_key(&self.secret_access_key, &date_stamp, &self.region);
        let signature = hex::encode(hmac_sha256(&sig_key, string_to_sign.as_bytes()));

        format!("https://{host}/{encoded_key}?{canonical_query}&X-Amz-Signature={signature}")
    }

    /// HEAD object using a presigned GET/HEAD or signed headers. Returns `Some(content_length)` if present.
    pub async fn head_object(&self, http: &reqwest::Client, key: &str) -> Result<Option<u64>, String> {
        let url = self.presign("HEAD", key, 60);
        let res = http
            .head(url)
            .send()
            .await
            .map_err(|e| format!("s3 head failed: {e}"))?;
        if res.status() == reqwest::StatusCode::NOT_FOUND {
            return Ok(None);
        }
        if !res.status().is_success() {
            return Err(format!("s3 head status: {}", res.status()));
        }
        let len = res
            .headers()
            .get(reqwest::header::CONTENT_LENGTH)
            .and_then(|v| v.to_str().ok())
            .and_then(|s| s.parse::<u64>().ok())
            .unwrap_or(0);
        Ok(Some(len))
    }

    pub async fn delete_object(&self, http: &reqwest::Client, key: &str) -> Result<(), String> {
        let url = self.presign("DELETE", key, 60);
        let res = http
            .delete(url)
            .send()
            .await
            .map_err(|e| format!("s3 delete failed: {e}"))?;
        if !res.status().is_success() && res.status() != reqwest::StatusCode::NOT_FOUND {
            return Err(format!("s3 delete status: {}", res.status()));
        }
        Ok(())
    }
}
