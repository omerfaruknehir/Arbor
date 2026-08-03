package app.xylune.chat.provider

import android.util.Base64
import android.util.Base64OutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.xylune.chat.data.AttachmentEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.ByteArrayOutputStream

internal val ProviderJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

internal fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
internal fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
internal fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
internal fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

internal fun attachmentContext(attachment: AttachmentEntity): String {
    val parts = buildList {
        add("Attached file: ${attachment.displayName} (${attachment.mimeType}, ${attachment.sizeBytes} bytes).")
        if (attachment.ocrJson.isNullOrBlank()) attachment.extractedText?.takeIf(String::isNotBlank)?.let {
            add("Extracted file text${if (it.length > MAX_TEXT_CONTEXT) " (truncated by Xylune)" else ""}:\n${it.take(MAX_TEXT_CONTEXT)}")
        }
        attachment.ocrJson?.takeIf(String::isNotBlank)?.let {
            add("Local OCR JSON (text and coordinates; the user still sees the untouched original${if (it.length > MAX_OCR_CONTEXT) "; truncated by Xylune" else ""}):\n${it.take(MAX_OCR_CONTEXT)}")
        }
        attachment.imageDescription?.takeIf(String::isNotBlank)?.let { add("Local image description:\n${it.take(MAX_DESCRIPTION_CONTEXT)}") }
        if (attachment.mimeType.startsWith("image/") && attachment.ocrJson != null) {
            add("The model is receiving OCR/local analysis for this image unless a native image part is also present.")
        }
    }
    return parts.joinToString("\n")
}

internal fun imageDataUrl(attachment: AttachmentEntity): String? {
    if (!attachment.mimeType.startsWith("image/")) return null
    val file = File(attachment.localPath)
    if (!file.exists()) return null
    return runCatching {
        val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true; BitmapFactory.decodeFile(file.absolutePath, it) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE_EDGE || bounds.outHeight / sample > MAX_IMAGE_EDGE) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().also { it.inSampleSize = sample }) ?: return null
        try {
            val output = ByteArrayOutputStream()
            val format = if (attachment.mimeType == "image/png" && bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            bitmap.compress(format, 88, output)
            val bytes = output.toByteArray()
            if (bytes.size > MAX_NATIVE_BYTES) return null
            val mime = if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
            "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()
}

internal fun fileDataUrl(attachment: AttachmentEntity): String? {
    val file = File(attachment.localPath)
    // JSON-based provider APIs require the base64 text to exist in memory at request
    // assembly time. Keep that path deliberately small and stream the source into the
    // encoder so a generated multi-megabyte text file can never allocate both the raw
    // byte array and its larger base64 copy at once.
    if (!file.isFile || file.length() > MAX_NATIVE_FILE_BYTES) return null
    return runCatching {
        val encoded = ByteArrayOutputStream(((file.length() + 2L) / 3L * 4L).toInt())
        Base64OutputStream(encoded, Base64.NO_WRAP).use { base64 ->
            file.inputStream().buffered(64 * 1024).use { input -> input.copyTo(base64, 64 * 1024) }
        }
        "data:${attachment.mimeType};base64," + encoded.toString(Charsets.US_ASCII.name())
    }.getOrNull()
}

internal fun dataUrlMime(value: String, fallback: String): String =
    value.substringAfter("data:", "").substringBefore(';').takeIf(String::isNotBlank) ?: fallback

internal fun parseHeaders(raw: String): Map<String, String> = try {
    ProviderJson.parseToJsonElement(raw).jsonObject.mapValues { (name, value) ->
        require(name.isNotBlank() && !name.contains('\n') && !name.contains('\r')) { "Invalid custom header name" }
        value.jsonPrimitive.content.also { require(!it.contains('\n') && !it.contains('\r')) { "Invalid custom header value" } }
    }
} catch (error: Throwable) {
    throw IllegalArgumentException("Custom headers must be a JSON object containing string values", error)
}

private const val MAX_IMAGE_EDGE = 2_048
private const val MAX_NATIVE_BYTES = 10 * 1024 * 1024
private const val MAX_NATIVE_FILE_BYTES = 4 * 1024 * 1024
private const val MAX_TEXT_CONTEXT = 24_000
private const val MAX_OCR_CONTEXT = 32_000
private const val MAX_DESCRIPTION_CONTEXT = 4_000
