package app.xylune.chat.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.xylune.chat.data.AttachmentEntity
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun AttachmentCard(
    attachment: AttachmentEntity,
    modelUsesFallback: Boolean = false,
    allowOcr: Boolean = true,
    onEnableOcr: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf(false) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(attachment.mimeType)) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                File(attachment.localPath).inputStream().use { input -> input.copyTo(output) }
            }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (attachment.isRasterImage()) {
                InlineImagePreview(attachment, onClick = { preview = true })
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 11.dp, end = 12.dp), verticalAlignment = Alignment.Top) {
                if (!attachment.isRasterImage()) Icon(
                    when { attachment.mimeType == "application/pdf" -> Icons.Outlined.PictureAsPdf; attachment.mimeType.startsWith("text/") -> Icons.Outlined.Description; else -> Icons.Outlined.FileOpen },
                    null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f).padding(start = if (attachment.isRasterImage()) 0.dp else 10.dp)) {
                    Text(attachment.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        uiText("${attachment.mimeType.substringAfter('/').uppercase()}  •  ${Formatter.formatShortFileSize(context, attachment.sizeBytes)}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (allowOcr && (attachment.ocrJson != null || attachment.imageDescription != null)) Text(
                        uiText(listOfNotNull(
                            "OCR ready".takeIf { attachment.ocrJson != null },
                            "Local description".takeIf { attachment.imageDescription != null },
                        ).joinToString("  •  ")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { preview = true }) { Icon(Icons.Outlined.FileOpen, null); Text(uiText("Preview"), Modifier.padding(start = 6.dp)) }
                IconButton(onClick = { saveAs.launch(attachment.displayName) }) { Icon(Icons.Outlined.Download, uiText("Save a copy")) }
                IconButton(onClick = { shareFile(context, attachment) }) { Icon(Icons.Outlined.Share, uiText("Share")) }
            }
            if (modelUsesFallback) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WarningAmber, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                uiText(if (attachment.ocrJson == null) "Attachment compatibility" else "OCR fallback enabled"),
                                Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            uiText(if (attachment.ocrJson == null) "This model cannot read the original attachment. OCR fallback will be prepared before sending."
                            else "This model receives OCR text and coordinates; you still see the untouched original."),
                            Modifier.fillMaxWidth().padding(top = 5.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = {
                            if (attachment.ocrJson == null) onEnableOcr?.invoke() else preview = true
                        }, enabled = attachment.ocrJson != null || onEnableOcr != null, modifier = Modifier.align(Alignment.End)) {
                            Text(uiText(if (attachment.ocrJson == null) "Prepare OCR" else "Open OCR view"))
                        }
                    }
                }
            }
        }
    }
    if (preview) AttachmentPreview(attachment, allowOcr, modelUsesFallback, onDismiss = { preview = false })
}

@Composable
private fun InlineImagePreview(attachment: AttachmentEntity, onClick: () -> Unit) {
    val file = remember(attachment.localPath) { File(attachment.localPath) }
    val dimensions = remember(file) { imageDimensions(file) }
    val ratio = (dimensions.first.toFloat() / dimensions.second).coerceIn(.82f, 2.4f)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 360.dp).aspectRatio(ratio)
            .clip(MaterialTheme.shapes.large).background(Color.Black).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(file, attachment.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Surface(
            color = MaterialTheme.colorScheme.scrim.copy(alpha = .56f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
        ) { Text(uiText("Tap to zoom"), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun AttachmentPreview(attachment: AttachmentEntity, allowOcr: Boolean, modelUsesFallback: Boolean, onDismiss: () -> Unit) {
    if (attachment.isRasterImage()) {
        RasterImagePreview(attachment, allowOcr, modelUsesFallback, onDismiss)
        return
    }
    var showOcr by remember(attachment.id) { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(attachment.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            uiText(when {
                                modelUsesFallback && attachment.ocrJson != null -> "Model receives OCR fallback • original preview is unchanged"
                                modelUsesFallback -> "Selected model cannot read the original attachment"
                                else -> attachment.mimeType
                            }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, uiText("Close")) }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
                    when {
                        attachment.mimeType == "application/pdf" -> PdfPreview(File(attachment.localPath))
                        attachment.isDiskTextPreviewable() -> DiskBackedTextPreview(File(attachment.localPath))
                        attachment.extractedText != null -> PagedExtractedTextPreview(attachment.extractedText)
                        else -> Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.FileOpen, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(uiText("No inline preview for this file type"), Modifier.padding(top = 12.dp))
                        }
                    }
                    attachment.imageDescription?.takeIf { allowOcr }?.let {
                        Text(uiText("Local image description"), Modifier.padding(top = 16.dp), fontWeight = FontWeight.SemiBold)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    attachment.ocrJson?.takeIf { allowOcr && showOcr }?.let { raw ->
                        val ocrText = remember(raw) { extractOcrText(raw) }
                        Text(uiText("Selectable OCR text"), Modifier.padding(top = 16.dp), fontWeight = FontWeight.SemiBold)
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(ocrText, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    val context = LocalContext.current
                    if (allowOcr && attachment.ocrJson != null) OutlinedButton(onClick = { showOcr = !showOcr }) {
                        Text(uiText(if (showOcr) "Hide OCR overlay" else "Show OCR overlay"))
                    }
                    OutlinedButton(onClick = { shareFile(context, attachment) }) { Icon(Icons.Outlined.Share, null); Text(uiText("Share"), Modifier.padding(start = 8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RasterImagePreview(attachment: AttachmentEntity, allowOcr: Boolean, modelUsesFallback: Boolean, onDismiss: () -> Unit) {
    var scale by remember(attachment.id) { mutableFloatStateOf(1f) }
    var translation by remember(attachment.id) { mutableStateOf(Offset.Zero) }
    var showOcr by remember(attachment.id) { mutableStateOf(false) }
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                ZoomableOcrImagePreview(
                    attachment = attachment,
                    showOcr = allowOcr && showOcr,
                    scale = scale,
                    translation = translation,
                    onTransform = { nextScale, nextTranslation -> scale = nextScale; translation = nextTranslation },
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(color = Color.Black.copy(alpha = .72f), modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(attachment.displayName, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(
                                uiText(when {
                                    modelUsesFallback && attachment.ocrJson != null -> "Original image • model receives OCR fallback"
                                    modelUsesFallback -> "Original image • selected model cannot receive it"
                                    else -> "Double-tap, pinch, or drag"
                                }),
                                color = Color.White.copy(alpha = .72f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        IconButton(onClick = { shareFile(context, attachment) }) { Icon(Icons.Outlined.Share, uiText("Share"), tint = Color.White) }
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, uiText("Close"), tint = Color.White) }
                    }
                }
                Surface(
                    color = Color.Black.copy(alpha = .76f),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                ) {
                    Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            scale = (scale / 1.6f).coerceAtLeast(1f)
                            if (scale == 1f) translation = Offset.Zero
                        }) { Icon(Icons.Outlined.ZoomOut, uiText("Zoom out"), tint = Color.White) }
                        Text(uiText("${(scale * 100).toInt()}%"), color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(52.dp))
                        IconButton(onClick = { scale = (scale * 1.6f).coerceAtMost(8f) }) { Icon(Icons.Outlined.ZoomIn, uiText("Zoom in"), tint = Color.White) }
                        IconButton(onClick = { scale = 1f; translation = Offset.Zero }) { Icon(Icons.Outlined.CenterFocusWeak, uiText("Fit image"), tint = Color.White) }
                        if (allowOcr && attachment.ocrJson != null) {
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = { showOcr = !showOcr }) { Text(uiText(if (showOcr) "OCR on" else "OCR"), color = Color.White) }
                        }
                    }
                }
                if (allowOcr && showOcr && attachment.ocrJson != null) {
                    val ocrText = remember(attachment.ocrJson) { extractOcrText(requireNotNull(attachment.ocrJson)) }
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 84.dp).heightIn(max = 220.dp),
                    ) {
                        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                            Text(uiText("Selectable OCR text"), fontWeight = FontWeight.SemiBold)
                            androidx.compose.foundation.text.selection.SelectionContainer { Text(ocrText, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableOcrImagePreview(
    attachment: AttachmentEntity,
    showOcr: Boolean,
    scale: Float,
    translation: Offset,
    onTransform: (Float, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val file = remember(attachment.localPath) { File(attachment.localPath) }
    val bounds = remember(attachment.ocrJson, showOcr) { if (showOcr) parseOcrBoxes(attachment.ocrJson) else emptyList() }
    val dimensions = remember(file) { imageDimensions(file) }
    var viewport by remember(file) { mutableStateOf(IntSize.Zero) }
    fun bounded(candidateScale: Float, candidate: Offset): Offset = clampImageTranslation(candidate, candidateScale, viewport, dimensions)
    LaunchedEffect(scale, viewport, dimensions) {
        val next = clampImageTranslation(translation, scale, viewport, dimensions)
        if (next != translation) onTransform(scale, next)
    }
    Box(
        modifier.background(Color.Black).onSizeChanged {
            viewport = it
            onTransform(scale, clampImageTranslation(translation, scale, it, dimensions))
        }
            .pointerInput(file, viewport, scale, translation) {
                detectTapGestures(onDoubleTap = { point ->
                    if (scale > 1.05f) onTransform(1f, Offset.Zero)
                    else {
                        val target = 2.75f
                        val center = Offset(viewport.width / 2f, viewport.height / 2f)
                        val focused = Offset((center.x - point.x) * (target - 1f), (center.y - point.y) * (target - 1f))
                        onTransform(target, clampImageTranslation(focused, target, viewport, dimensions))
                    }
                })
            }
            .pointerInput(file, viewport, scale, translation) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 8f)
                    if (nextScale <= 1.001f) {
                        onTransform(1f, Offset.Zero)
                    } else {
                        val ratio = nextScale / scale.coerceAtLeast(1f)
                        val center = Offset(viewport.width / 2f, viewport.height / 2f)
                        val next = Offset(
                            translation.x * ratio - (centroid.x - center.x) * (ratio - 1f) + pan.x,
                            translation.y * ratio - (centroid.y - center.y) * (ratio - 1f) + pan.y,
                        )
                        onTransform(nextScale, bounded(nextScale, next))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = translation.x, translationY = translation.y)) {
                AsyncImage(file, attachment.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(Modifier.fillMaxSize()) {
                    bounds.forEach { box ->
                        val imageRatio = dimensions.first.toFloat() / dimensions.second
                        val canvasRatio = size.width / size.height
                        val renderedWidth: Float
                        val renderedHeight: Float
                        val originX: Float
                        val originY: Float
                        if (imageRatio > canvasRatio) {
                            renderedWidth = size.width; renderedHeight = size.width / imageRatio; originX = 0f; originY = (size.height - renderedHeight) / 2
                        } else {
                            renderedHeight = size.height; renderedWidth = size.height * imageRatio; originX = (size.width - renderedWidth) / 2; originY = 0f
                        }
                        val left = originX + box[0] / dimensions.first * renderedWidth
                        val top = originY + box[1] / dimensions.second * renderedHeight
                        val width = (box[2] - box[0]) / dimensions.first * renderedWidth
                        val height = (box[3] - box[1]) / dimensions.second * renderedHeight
                        drawRect(Color(0x55FFCC00), Offset(left, top), Size(width, height))
                        drawRect(Color(0xFFFFB300), Offset(left, top), Size(width, height), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
    }
}

private fun clampImageTranslation(candidate: Offset, scale: Float, viewport: IntSize, image: Pair<Int, Int>): Offset {
    if (scale <= 1f || viewport.width <= 0 || viewport.height <= 0) return Offset.Zero
    val fit = min(viewport.width / image.first.toFloat(), viewport.height / image.second.toFloat())
    val renderedWidth = image.first * fit * scale
    val renderedHeight = image.second * fit * scale
    val maxX = max(0f, (renderedWidth - viewport.width) / 2f)
    val maxY = max(0f, (renderedHeight - viewport.height) / 2f)
    return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
}

private fun imageDimensions(file: File): Pair<Int, Int> = BitmapFactory.Options().also {
    it.inJustDecodeBounds = true
    BitmapFactory.decodeFile(file.absolutePath, it)
}.let { it.outWidth.coerceAtLeast(1) to it.outHeight.coerceAtLeast(1) }

@Composable
private fun PdfPreview(file: File) {
    var pageIndex by remember(file.absolutePath) { mutableIntStateOf(0) }
    val rendered by produceState<PdfPageRender?>(null, file.absolutePath, pageIndex) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val safePage = pageIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                        renderer.openPage(safePage).use { page ->
                            val scale = minOf(2.5f, 1800f / page.width.coerceAtLeast(page.height))
                            val bitmap = createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            PdfPageRender(bitmap, safePage, renderer.pageCount)
                        }
                    }
                }
            }.getOrElse { PdfPageRender(error = it.message ?: "This PDF could not be rendered") }
        }
    }
    val page = rendered
    when {
        page == null -> Text(uiText("Rendering PDF…"), Modifier.padding(32.dp))
        page.error != null -> Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .25f), shape = MaterialTheme.shapes.medium) {
            Text(uiText("PDF preview unavailable: ${page.error}"), Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.error)
        }
        page.bitmap != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = Color.White, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Image(page.bitmap.asImageBitmap(), "PDF page ${page.pageIndex + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = page.pageIndex > 0) {
                    Icon(Icons.AutoMirrored.Outlined.NavigateBefore, uiText("Previous page"))
                }
                Text(uiText("Page ${page.pageIndex + 1} of ${page.pageCount}"), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { pageIndex = (pageIndex + 1).coerceAtMost(page.pageCount - 1) }, enabled = page.pageIndex + 1 < page.pageCount) {
                    Icon(Icons.AutoMirrored.Outlined.NavigateNext, uiText("Next page"))
                }
            }
        }
    }
}

private data class PdfPageRender(
    val bitmap: Bitmap? = null,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val error: String? = null,
)

private fun parseOcrBoxes(raw: String?): List<FloatArray> = runCatching {
    if (raw == null) return emptyList()
    Json.parseToJsonElement(raw).jsonObject["pages"]!!.jsonArray.first().jsonObject["elements"]!!.jsonArray.mapNotNull { element ->
        element.jsonObject["box"]?.jsonObject?.let { box ->
            listOf("left", "top", "right", "bottom").map { name -> box[name]!!.jsonPrimitive.content.toFloat() }.toFloatArray()
        }
    }
}.getOrDefault(emptyList())

private fun extractOcrText(raw: String): String = runCatching {
    Json.parseToJsonElement(raw).jsonObject["pages"]!!.jsonArray.joinToString("\n\n") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
}.getOrDefault(raw)

private fun shareFile(context: android.content.Context, attachment: AttachmentEntity) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(attachment.localPath))
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = attachment.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share ${attachment.displayName}"))
}

private fun AttachmentEntity.isRasterImage(): Boolean = mimeType in setOf(
    "image/png", "image/jpeg", "image/webp", "image/gif", "image/bmp", "image/heif", "image/heic",
)
