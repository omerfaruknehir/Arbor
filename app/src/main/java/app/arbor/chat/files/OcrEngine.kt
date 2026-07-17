package app.arbor.chat.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import app.arbor.chat.data.AttachmentDao
import app.arbor.chat.data.AttachmentEntity
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrEngine(
    private val context: Context,
    private val attachmentDao: AttachmentDao,
) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun analyze(attachment: AttachmentEntity): AttachmentEntity = withContext(Dispatchers.Default) {
        val inputImage = if (attachment.mimeType.startsWith("image/")) {
            InputImage.fromFilePath(context, Uri.fromFile(File(attachment.localPath)))
        } else null
        val pdfPageCount = if (attachment.mimeType == "application/pdf") pdfPageCount(File(attachment.localPath)) else null
        val pages = when {
            inputImage != null -> listOf(recognize(inputImage, 0))
            attachment.mimeType == "application/pdf" -> recognizePdf(File(attachment.localPath))
            else -> return@withContext attachment
        }
        val allText = pages.joinToString("\n\n") { it.second.text }.take(1_000_000)
        val json = buildJsonObject {
            put("version", JsonPrimitive(1))
            put("engine", JsonPrimitive("ML Kit on-device text recognition"))
            pdfPageCount?.let { total ->
                put("totalPages", JsonPrimitive(total))
                put("processedPages", JsonPrimitive(pages.size))
                put("truncated", JsonPrimitive(total > pages.size))
            }
            put("pages", buildJsonArray {
                pages.forEach { (page, text) -> add(pageJson(page, text)) }
            })
        }.toString()
        attachment.copy(
            ocrJson = json,
            extractedText = allText.ifBlank { attachment.extractedText },
        ).also { attachmentDao.upsert(it) }
    }

    private fun pdfPageCount(file: File): Int = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { it.pageCount }
        }
    }.getOrDefault(0)

    private suspend fun recognizePdf(file: File): List<Pair<Int, Text>> = withContext(Dispatchers.IO) {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        try {
            (0 until minOf(renderer.pageCount, 12)).map { index ->
                renderer.openPage(index).use { page ->
                    val scale = minOf(2f, 2048f / page.width.coerceAtLeast(page.height))
                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    index to recognize(InputImage.fromBitmap(bitmap, 0), index).second.also { bitmap.recycle() }
                }
            }
        } finally {
            renderer.close()
            descriptor.close()
        }
    }

    private suspend fun recognize(image: InputImage, page: Int): Pair<Int, Text> = page to recognizer.process(image).await()

    private fun pageJson(page: Int, text: Text): JsonObject = buildJsonObject {
        put("page", JsonPrimitive(page))
        put("text", JsonPrimitive(text.text))
        put("elements", buildJsonArray {
            text.textBlocks.forEachIndexed { blockIndex, block ->
                block.lines.forEachIndexed { lineIndex, line ->
                    line.elements.forEach { element ->
                        add(buildJsonObject {
                            put("text", JsonPrimitive(element.text))
                            put("block", JsonPrimitive(blockIndex))
                            put("line", JsonPrimitive(lineIndex))
                            element.boundingBox?.let { rect ->
                                put("box", buildJsonObject {
                                    put("left", JsonPrimitive(rect.left)); put("top", JsonPrimitive(rect.top))
                                    put("right", JsonPrimitive(rect.right)); put("bottom", JsonPrimitive(rect.bottom))
                                })
                            }
                            element.confidence?.let { put("confidence", JsonPrimitive(it)) }
                        })
                    }
                }
            }
        })
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
