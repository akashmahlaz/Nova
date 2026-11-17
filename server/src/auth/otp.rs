use hmac::{Hmac, KeyInit, Mac};
use rand::RngExt;
use sha2::{Digest, Sha256};

type HmacSha256 = Hmac<Sha256>;

/// Six-digit code from the thread-local CSPRNG (rand 0.10, seeded from the OS).
pub fn generate_code() -> String {
    let n: u32 = rand::rng().random_range(0..1_000_000);
    format!("{n:06}")
}

/// Opaque refresh token: 32 random bytes, hex encoded. Only its SHA-256 is stored.
pub fn new_refresh_token() -> String {
    let mut bytes = [0u8; 32];
    rand::rng().fill(&mut bytes[..]);
    hex::encode(bytes)
}

/// Binds the code to channel, destination and purpose so a code for one can never verify another.
fn keyed(secret: &str, channel: &str, destination: &str, purpose: &str) -> HmacSha256 {
    // HMAC accepts keys of any length, so this cannot fail.
    let mut mac = HmacSha256::new_from_slice(secret.as_bytes()).expect("hmac takes any key length");
    mac.update(channel.as_bytes());
    mac.update(b"|");
    mac.update(destination.as_bytes());
    mac.update(b"|");
    mac.update(purpose.as_bytes());
    mac.update(b"|");
    mac
}

pub fn hash_code(secret: &str, channel: &str, destination: &str, purpose: &str, code: &str) -> String {
    let mut mac = keyed(secret, channel, destination, purpose);
    mac.update(code.as_bytes());
    hex::encode(mac.finalize().into_bytes())
}

pub fn verify_code(secret: &str, channel: &str, destination: &str, purpose: &str, code: &str, stored_hex: &str) -> bool {
    let Ok(expected) = hex::decode(stored_hex) else {
        return false;
    };
    let mut mac = keyed(secret, channel, destination, purpose);
    mac.update(code.as_bytes());
    mac.verify_slice(&expected).is_ok()
}

/// Shown back to the app so the user can confirm where the code went, e.g. `•••• 4567`, `m***@gmail.com`.
pub fn mask(channel: &str, destination: &str) -> String {
    match channel {
        "phone" => {
            let tail: String = destination.chars().rev().take(4).collect::<Vec<_>>().into_iter().rev().collect();
            format!("•••• {tail}")
        }
        _ => match destination.split_once('@') {
            Some((local, domain)) => {
                let first = local.chars().next().unwrap_or('*');
                format!("{first}***@{domain}")
            }
            None => "***".into(),
        },
    }
}

pub fn sha256_hex(input: &str) -> String {
    hex::encode(Sha256::digest(input.as_bytes()))
}
