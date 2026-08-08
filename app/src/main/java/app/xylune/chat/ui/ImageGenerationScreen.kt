package app.xylune.chat.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.xylune.chat.XyluneApplication
import app.xylune.chat.data.AttachmentEntity
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.SendMode
import app.xylune.chat.provider.ImageInputMode
import app.xylune.chat.provider.imageModelActionLabel
import app.xylune.chat.provider.imageModelCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private data class GeneratedImageHistoryEntry(
    val attachment: AttachmentEntity,
    val prompt: String,
    val modelId: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageGenerationScreen(
    viewModel: ChatViewModel,
    openDrawer: (() -> Unit)?,
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val favoriteModels by viewModel.favoriteModels.collectAsStateWithLifecycle()
    val recentModels by viewModel.recentModels.collectAsStateWithLifecycle()
    val credentialRevision by viewModel.credentialRevision.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val staged by viewModel.stagedAttachments.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val generating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val chromeBlurStrength by viewModel.chromeBlurStrength.collectAsStateWithLifecycle()
    val chromeEdgeSoftness by viewModel.chromeEdgeSoftness.collectAsStateWithLifecycle()
    val chromeOverlayOpacity by viewModel.chromeOverlayOpacity.collectAsStateWithLifecycle()

    val currentProvider = remember(conversation?.selectedProviderId, providers) {
        providers.firstOrNull { it.id == conversation?.selectedProviderId }
    }
    val currentModel = remember(conversation?.selectedModelId, models) {
        models.firstOrNull { it.modelId == conversation?.selectedModelId }
    }
    val capabilities = remember(currentProvider, currentModel) {
        imageModelCapabilities(currentProvider, currentModel)
    }
    val configuredProviders = remember(providers, credentialRevision) {
        viewModel.configuredProviders(providers)
    }
    var showModelPicker by remember { mutableStateOf(false) }
    var showReferenceMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as XyluneApplication).container
    }
    var generatedHistory by remember(conversation?.id) { mutableStateOf(emptyList<GeneratedImageHistoryEntry>()) }
    LaunchedEffect(conversation?.id, generating, pending.size) {
        val id = conversation?.id ?: return@LaunchedEffect
        generatedHistory = withContext(Dispatchers.IO) {
            val generated = mutableListOf<GeneratedImageHistoryEntry>()
            for (attachment in container.database.attachmentDao().forConversation(id)) {
                if (!attachment.mimeType.startsWith("image/") || attachment.mimeType == "image/svg+xml") continue
                val nodeId = attachment.messageNodeId ?: continue
                val assistant = container.repository.message(nodeId) ?: continue
                if (assistant.role != MessageRole.ASSISTANT) continue
                val prompt = assistant.parentNodeId
                    ?.let { container.repository.message(it) }
                    ?.takeIf { it.role == MessageRole.USER }
                    ?.content
                    .orEmpty()
                generated += GeneratedImageHistoryEntry(attachment, prompt, assistant.modelId)
            }
            generated.sortedByDescending { it.attachment.createdAt }.take(50)
        }
    }

    val rasterReferences = staged.filter { it.mimeType.startsWith("image/") && it.mimeType != "image/svg+xml" }
    val invalidAttachments = staged.size - rasterReferences.size
    val blockedReason = when {
        currentProvider == null || currentModel == null || capabilities == null -> "Choose an image model to continue."
        invalidAttachments > 0 -> "Remove non-image attachments before creating an image."
        capabilities.inputMode == ImageInputMode.NONE && rasterReferences.isNotEmpty() ->
            "${currentModel.displayName} does not accept reference images."
        rasterReferences.size > capabilities.maxInputImages ->
            "${currentModel.displayName} accepts at most ${capabilities.maxInputImages} reference images."
        capabilities.inputMode == ImageInputMode.REQUIRED && rasterReferences.isEmpty() ->
            "Add at least one reference image for ${currentModel.displayName}."
        else -> null
    }
    val canSubmit = !importing && draft.isNotBlank() && blockedReason == null

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(16)) { uris ->
        uris.forEach(viewModel::import)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        val file = pendingCameraFile
        pendingCameraUri = null
        pendingCameraFile = null
        if (saved && uri != null) viewModel.import(uri) else file?.delete()
    }
    fun addPhotos() {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun takePhoto() {
        val file = File(context.cacheDir, "camera/${UUID.randomUUID()}.jpg").also { it.parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        pendingCameraFile = file
        pendingCameraUri = uri
        camera.launch(uri)
    }

    val listState = rememberLazyListState()
    val blurState = rememberXyluneBackdropBlurState()
    val topAppBarState = rememberTopAppBarState()
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    Scaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            ChatCollapsingTranslucentTopBar(
                title = conversation?.title ?: "Images",
                scrollBehavior = topAppBarScrollBehavior,
                blurState = blurState,
                blurStrength = chromeBlurStrength,
                edgeSoftness = chromeEdgeSoftness,
                overlayOpacity = chromeOverlayOpacity,
                navigationIcon = {
                    openDrawer?.let { drawer ->
                        IconButton(onClick = drawer) { Icon(Icons.Outlined.Menu, uiText("Open conversations")) }
                    }
                },
                actions = {},
                modelSelector = {
                    Surface(
                        onClick = { if (!generating) showModelPicker = true },
                        enabled = !generating,
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Outlined.Image, null, Modifier.size(17.dp))
                            Column {
                                Text(
                                    uiText(currentModel?.displayName ?: "Choose image model"),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (currentModel != null && currentProvider != null) {
                                    Text(
                                        imageModelActionLabel(currentProvider, currentModel),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().imePadding()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .xyluneBackdropBlur(
                            state = blurState,
                            strength = chromeBlurStrength,
                            edgeSoftness = chromeEdgeSoftness,
                            overlayOpacity = chromeOverlayOpacity,
                            tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.46f),
                            edge = XyluneBlurEdge.BOTTOM,
                            panelHeight = 88.dp,
                            expandToMeasuredHeight = true,
                        ),
                ) {
                    Column(
                        Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (rasterReferences.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 82.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                            ) {
                                items(rasterReferences, key = AttachmentEntity::id) { attachment ->
                                    ImageReferenceThumbnail(
                                        attachment = attachment,
                                        onRemove = { viewModel.removeStaged(attachment.id) },
                                    )
                                }
                            }
                        }
                        blockedReason?.let { reason ->
                            Text(
                                reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (invalidAttachments > 0 || capabilities?.inputMode == ImageInputMode.REQUIRED && rasterReferences.isEmpty()) {
                                    MaterialTheme.colorScheme.error
                                } else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { showReferenceMenu = true },
                                enabled = !importing && capabilities?.supportsEditing == true,
                            ) {
                                Icon(Icons.Outlined.Add, uiText("Add reference image"))
                            }
                            OutlinedTextField(
                                value = draft,
                                onValueChange = viewModel::setDraft,
                                modifier = Modifier.weight(1f).heightIn(min = 54.dp, max = 170.dp),
                                shape = MaterialTheme.shapes.extraLarge,
                                maxLines = 7,
                                enabled = !importing,
                                placeholder = {
                                    Text(
                                        uiText(when {
                                            capabilities?.inputMode == ImageInputMode.REQUIRED && rasterReferences.isEmpty() -> "Add an image, then describe the edit…"
                                            rasterReferences.isNotEmpty() -> "Describe the changes…"
                                            else -> "Describe an image…"
                                        }),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                            if (generating) {
                                IconButton(onClick = viewModel::stop, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Filled.Stop, uiText("Stop image generation"))
                                }
                                Spacer(Modifier.width(2.dp))
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (canSubmit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (canSubmit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp).combinedClickable(
                                    enabled = canSubmit,
                                    onClick = { viewModel.send(if (generating) SendMode.QUEUE else SendMode.SEND_NOW) },
                                    onLongClick = { if (generating) viewModel.send(SendMode.QUEUE) },
                                ),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (importing) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                                    else Icon(
                                        if (generating) Icons.Outlined.Schedule else Icons.Filled.ArrowUpward,
                                        if (generating) "Queue image" else if (rasterReferences.isNotEmpty()) "Edit image" else "Generate image",
                                    )
                                }
                            }
                        }
                        if (pending.isNotEmpty()) {
                            Text(
                                uiText("${pending.size} image request${if (pending.size == 1) "" else "s"} queued"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 58.dp),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().xyluneBackdropSource(blurState)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = padding.calculateTopPadding() + 14.dp,
                    bottom = padding.calculateBottomPadding() + 14.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (generating && conversation != null && currentProvider != null && currentModel != null && capabilities != null) {
                    item(key = "image-progress") {
                        ImageGenerationProgressCard(
                            conversationId = conversation!!.id,
                            providerName = currentProvider.displayName,
                            modelName = currentModel.displayName,
                            supportsProgressivePreview = capabilities.supportsProgressivePreview,
                            onStop = viewModel::stop,
                        )
                    }
                }
                if (generatedHistory.isEmpty() && !generating) {
                    item(key = "empty-images") {
                        ImageWorkspaceEmptyState(
                            supportsEditing = capabilities?.supportsEditing == true,
                            maxReferences = capabilities?.maxInputImages ?: 0,
                        )
                    }
                } else {
                    items(generatedHistory, key = { it.attachment.id }) { entry ->
                        GeneratedImageConversationItem(entry)
                    }
                }
            }
        }
    }

    if (showReferenceMenu) {
        ModalBottomSheet(onDismissRequest = { showReferenceMenu = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    uiText("Reference image"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
                ListItem(
                    headlineContent = { Text(uiText("Photos")) },
                    supportingContent = { Text(uiText("Choose one or more reference images")) },
                    leadingContent = { Icon(Icons.Outlined.Image, null) },
                    modifier = Modifier.combinedClickable(
                        onClick = { showReferenceMenu = false; addPhotos() },
                        onLongClick = { showReferenceMenu = false; addPhotos() },
                    ),
                )
                ListItem(
                    headlineContent = { Text(uiText("Camera")) },
                    supportingContent = { Text(uiText("Take a photo to use as a reference")) },
                    leadingContent = { Icon(Icons.Outlined.CameraAlt, null) },
                    modifier = Modifier.combinedClickable(
                        onClick = { showReferenceMenu = false; takePhoto() },
                        onLongClick = { showReferenceMenu = false; takePhoto() },
                    ),
                )
                capabilities?.let { caps ->
                    Text(
                        uiText(if (caps.maxInputImages > 0) "${currentModel?.displayName.orEmpty()} accepts up to ${caps.maxInputImages} reference images."
                        else "This model does not accept reference images."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            providers = configuredProviders,
            models = allModels.filter { model -> configuredProviders.any { it.id == model.providerId } },
            selectedProviderId = conversation?.selectedProviderId,
            selectedModelId = conversation?.selectedModelId,
            favoriteKeys = favoriteModels,
            recentKeys = recentModels,
            onToggleFavorite = viewModel::toggleFavoriteModel,
            onSelect = viewModel::selectModel,
            onDismiss = { showModelPicker = false },
        )
    }
}

@Composable
private fun ImageWorkspaceEmptyState(
    supportsEditing: Boolean,
    maxReferences: Int,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Image, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            uiText("What are we creating?"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            uiText(if (supportsEditing) {
                "Describe a new image, or add${if (maxReferences > 0) " up to $maxReferences" else ""} reference images and describe the edit."
            } else {
                "Describe the image you want. This model generates new images without reference-image editing."
            }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ImageReferenceThumbnail(
    attachment: AttachmentEntity,
    onRemove: () -> Unit,
) {
    Box(Modifier.size(78.dp)) {
        LocalRasterImage(
            attachment = attachment,
            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
            contentScale = ContentScale.Crop,
        )
        Surface(
            onClick = onRemove,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.scrim.copy(alpha = .62f),
            contentColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, uiText("Remove ${attachment.displayName}"), Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun GeneratedImageConversationItem(entry: GeneratedImageHistoryEntry) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (entry.prompt.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(.88f),
                ) {
                    Text(
                        entry.prompt,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        LocalRasterImage(
            attachment = entry.attachment,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.extraLarge),
            contentScale = ContentScale.Fit,
        )
        Text(
            uiText(buildString {
                append(entry.modelId ?: "Image")
                append(" · ")
                append(entry.attachment.displayName)
            }),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LocalRasterImage(
    attachment: AttachmentEntity,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val path = attachment.thumbnailPath ?: attachment.localPath
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = attachment.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
