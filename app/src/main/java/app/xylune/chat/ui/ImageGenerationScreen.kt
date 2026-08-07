package app.xylune.chat.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.xylune.chat.XyluneApplication
import app.xylune.chat.data.AttachmentEntity
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.SendMode
import app.xylune.chat.provider.ImageInputMode
import app.xylune.chat.provider.imageModelCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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

    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as XyluneApplication).container
    }
    var generatedHistory by remember(conversation?.id) { mutableStateOf(emptyList<AttachmentEntity>()) }
    LaunchedEffect(conversation?.id, generating, pending.size) {
        val id = conversation?.id ?: return@LaunchedEffect
        generatedHistory = withContext(Dispatchers.IO) {
            container.database.attachmentDao().forConversation(id)
                .asSequence()
                .filter { it.messageNodeId != null && it.mimeType.startsWith("image/") }
                .filter { attachment ->
                    val nodeId = attachment.messageNodeId ?: return@filter false
                    container.repository.message(nodeId)?.role == MessageRole.ASSISTANT
                }
                .sortedByDescending(AttachmentEntity::createdAt)
                .take(30)
                .toList()
        }
    }

    val rasterReferences = staged.filter { it.mimeType.startsWith("image/") && it.mimeType != "image/svg+xml" }
    val invalidAttachments = staged.size - rasterReferences.size
    val blockedReason = when {
        currentProvider == null || currentModel == null || capabilities == null -> "Choose an image model to continue."
        invalidAttachments > 0 -> "Remove non-image attachments before creating an image."
        capabilities.inputMode == ImageInputMode.NONE && rasterReferences.isNotEmpty() ->
            "${currentModel.displayName} generates new images but does not accept reference images."
        rasterReferences.size > capabilities.maxInputImages ->
            "${currentModel.displayName} accepts at most ${capabilities.maxInputImages} reference images."
        capabilities.inputMode == ImageInputMode.REQUIRED && rasterReferences.isEmpty() ->
            "${currentModel.displayName} is an editing model. Add at least one reference image."
        else -> null
    }
    val canSubmit = !importing && draft.isNotBlank() && blockedReason == null

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { uris ->
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Images", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (rasterReferences.isNotEmpty()) "Editing" else "Generation",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    openDrawer?.let { drawer ->
                        IconButton(onClick = drawer) { Icon(Icons.Outlined.Menu, "Open conversations") }
                    }
                },
                actions = {
                    TextButton(onClick = { showModelPicker = true }) {
                        Icon(Icons.Outlined.Image, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            currentModel?.displayName ?: "Choose model",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth().imePadding(),
            ) {
                Column(
                    Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    capabilities?.let { imageCapabilities ->
                        ImageRequestModeCard(
                            modelName = currentModel?.displayName.orEmpty(),
                            capabilities = imageCapabilities,
                            referenceImageCount = rasterReferences.size,
                            invalidAttachmentCount = invalidAttachments,
                            blockedReason = blockedReason,
                            onAddReferenceImage = ::addPhotos,
                        )
                    }
                    if (staged.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 94.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) {
                            items(staged, key = AttachmentEntity::id) { attachment ->
                                ImageReferenceChip(
                                    attachment = attachment,
                                    onRemove = { viewModel.removeStaged(attachment.id) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = viewModel::setDraft,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        enabled = !importing,
                        placeholder = {
                            Text(
                                when {
                                    capabilities?.inputMode == ImageInputMode.REQUIRED && rasterReferences.isEmpty() ->
                                        "Add an image, then describe the edit…"
                                    rasterReferences.isNotEmpty() -> "Describe how to edit the reference image${if (rasterReferences.size == 1) "" else "s"}…"
                                    else -> "Describe the image you want to create…"
                                },
                            )
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (capabilities?.supportsEditing == true) {
                            IconButton(onClick = ::addPhotos, enabled = !importing) {
                                Icon(Icons.Outlined.Collections, "Choose reference images")
                            }
                            IconButton(onClick = ::takePhoto, enabled = !importing) {
                                Icon(Icons.Outlined.CameraAlt, "Take a reference photo")
                            }
                        }
                        if (pending.isNotEmpty()) {
                            AssistChip(
                                onClick = {},
                                label = { Text("${pending.size} queued") },
                                leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp)) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                viewModel.send(if (generating) SendMode.QUEUE else SendMode.SEND_NOW)
                            },
                            enabled = canSubmit,
                        ) {
                            if (importing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.Send, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    generating -> "Queue image"
                                    rasterReferences.isNotEmpty() -> "Edit image"
                                    else -> "Generate"
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
            if (generatedHistory.isNotEmpty()) {
                item(key = "latest-heading") {
                    Text("Latest", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                item(key = generatedHistory.first().id) {
                    GeneratedImageCard(generatedHistory.first(), large = true)
                }
                if (generatedHistory.size > 1) {
                    item(key = "history-heading") {
                        Text("Previous images", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    item(key = "history-row") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(generatedHistory.drop(1), key = AttachmentEntity::id) { attachment ->
                                GeneratedImageCard(attachment, large = false)
                            }
                        }
                    }
                }
            } else if (!generating) {
                item(key = "empty-images") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 22.dp, vertical = 34.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Create or edit an image", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Choose an image model, add reference images when supported, and describe the result you want.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
private fun ImageReferenceChip(
    attachment: AttachmentEntity,
    onRemove: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LocalRasterImage(
                attachment = attachment,
                modifier = Modifier.size(62.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.width(112.dp)) {
                Text(attachment.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                Text("Reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRemove, contentPadding = PaddingValues(0.dp)) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun GeneratedImageCard(
    attachment: AttachmentEntity,
    large: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = if (large) Modifier.fillMaxWidth() else Modifier.width(196.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            LocalRasterImage(
                attachment = attachment,
                modifier = (if (large) Modifier.fillMaxWidth() else Modifier.width(196.dp))
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.extraLarge),
                contentScale = ContentScale.Fit,
            )
            Text(
                attachment.displayName,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
