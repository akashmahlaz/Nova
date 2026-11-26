package com.nova.driver.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Turns a camera shot or picked file into upload bytes: photos are downscaled JPEGs, PDFs pass through. */
object DocumentImages {
    private const val MAX_EDGE = 2000
    private const val MAX_PDF_BYTES = 10 * 1024 * 1024

    data class Prepared(val bytes: ByteArray, val contentType: String, val fileName: String)

    /** A fresh file the camera app can write into (shared through our FileProvider). */
    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3_600_000) it.delete() }
        val file = File(dir, "doc_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    suspend fun prepare(context: Context, uri: Uri): Prepared = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri).orEmpty()
        if (type == "application/pdf") {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("unreadable")
            require(bytes.size <= MAX_PDF_BYTES) { "too_large" }
            return@withContext Prepared(bytes, "application/pdf", "document.pdf")
        }
        val bitmap = decode(context, uri)
        val scaled = scale(bitmap)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        Prepared(out.toByteArray(), "image/jpeg", "document.jpg")
    }

    private fun decode(context: Context, uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val edge = maxOf(info.size.width, info.size.height)
                if (edge > MAX_EDGE) {
                    val f = MAX_EDGE.toFloat() / edge
                    decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                }
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("unreadable")
        }

    private fun scale(b: Bitmap): Bitmap {
        val edge = maxOf(b.width, b.height)
        if (edge <= MAX_EDGE) return b
        val f = MAX_EDGE.toFloat() / edge
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
    }
}
