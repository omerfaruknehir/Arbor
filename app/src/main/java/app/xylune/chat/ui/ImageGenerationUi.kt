package app.xylune.chat.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.xylune.chat.generation.StreamingPreviewStore
import app.xylune.chat.provider.ImageInputMode
import app.xylune.chat.provider.ImageModelCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ImageRequestModeCard(
    modelName: String,
    capabilities: ImageModelCapabilities,
    referenceImageCount: Int,
    invalidAttachmentCount: Int,
    blockedReason: String?,
    onAddReferenceImage: () -> Unit,
) {
    val editing = referenceImageCount > 0 || capabilities.inputMode == ImageInputMode.REQUIRED
    Surface(
        color = if (blockedReason == null) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (blockedReason == null) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (editing) Icons.Outlined.AutoFixHigh else Icons.Outlined.Image,
                    null,
                    Modifier.size(18.dp),
                )
                Text(
                    uiText(if (editing) "Edit image" else "Create image"),
                    Modifier.padding(start = 8.dp).weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (capabilities.inputMode != ImageInputMode.NONE &&
                    referenceImageCount < capabilities.maxInputImages
                ) {
                    TextButton(onClick = onAddReferenceImage) {
                        Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(17.dp))
                        Text(uiText(if (referenceImageCount == 0) "Add image" else "Add another"))
                    }
                }
            }
            Text(
                uiText(blockedReason ?: when {
                    capabilities.inputMode == ImageInputMode.REQUIRED && referenceImageCount == 0 ->
                        "$modelName requires at least one reference image."
                    referenceImageCount > 0 ->
                        "$referenceImageCount reference image${if (referenceImageCount == 1) "" else "s"} · describe the changes you want."
                    capabilities.inputMode == ImageInputMode.OPTIONAL ->
                        "Describe what to create, or add up to ${capabilities.maxInputImages} reference images to edit."
                    else -> "Describe the image you want to create."
                }),
                style = MaterialTheme.typography.bodySmall,
            )
            if (invalidAttachmentCount > 0 && blockedReason == null) {
                Text(
                    uiText("Image requests cannot include ordinary files. Remove the non-image attachment${if (invalidAttachmentCount == 1) "" else "s"} first."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun ImageGenerationProgressCard(
    conversationId: String,
    providerName: String,
    modelName: String,
    supportsProgressivePreview: Boolean,
    onStop: () -> Unit,
) {
    val previews by StreamingPreviewStore.previews.collectAsStateWithLifecycle()
    val current = remember(previews, conversationId) {
        previews.values
            .asSequence()
            .filter { it.conversationId == conversationId }
            .filter { it.generatedImagePreview != null }
            .maxByOrNull { it.updatedAt }
    }
    val preview = current?.generatedImagePreview
    val bitmap by produceState<ImageBitmap?>(initialValue = null, current?.updatedAt) {
        val bytes = preview?.bytes
        value = if (bytes == null) null else withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text(
                        uiText(if (preview == null) "Generating image" else "Rendering image"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        uiText("$providerName · $modelName"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, uiText("Stop image generation"))
                }
            }

            val decoded = bitmap
            if (decoded != null) {
                Crossfade(targetState = decoded, label = "ImagePreviewCrossfade") { frame ->
                    Image(
                        bitmap = frame,
                        contentDescription = uiText("Current generated image preview"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp)),
                    )
                }
                val index = (current?.generatedImagePreviewIndex ?: 0) + 1
                val count = current?.generatedImagePreviewCount
                Text(
                    uiText(buildString {
                        append("Provider preview ").append(index)
                        if (count != null && count > 0) append(" of ").append(count)
                        append(" · the final image may still change.")
                    }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Image,
                            null,
                            Modifier.size(42.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    uiText(if (supportsProgressivePreview) {
                        "Waiting for the first provider-rendered preview…"
                    } else {
                        "This provider returns the final image when generation finishes."
                    }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
