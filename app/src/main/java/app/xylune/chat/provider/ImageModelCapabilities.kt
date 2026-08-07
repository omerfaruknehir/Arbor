package app.xylune.chat.provider

import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity

enum class ImageInputMode {
    NONE,
    OPTIONAL,
    REQUIRED,
}

data class ImageModelCapabilities(
    val inputMode: ImageInputMode,
    val maxInputImages: Int,
    val supportsProgressivePreview: Boolean,
) {
    val supportsEditing: Boolean
        get() = inputMode != ImageInputMode.NONE
}

/**
 * UX-facing image capabilities. Keep these separate from the generic vision flag:
 * a model can understand images in chat without accepting reference images for an
 * image-generation request, and an image model can require an input image.
 */
fun imageModelCapabilities(
    provider: ProviderEntity?,
    model: ModelEntity?,
): ImageModelCapabilities? {
    if (provider == null || model?.supportsImageGeneration != true) return null

    if (ModelRequestPolicy.isQwenCloudImageModel(provider, model)) {
        return when {
            ModelRequestPolicy.qwenCloudImageRequiresInputImage(model) -> ImageModelCapabilities(
                inputMode = ImageInputMode.REQUIRED,
                maxInputImages = 3,
                supportsProgressivePreview = false,
            )
            ModelRequestPolicy.qwenCloudImageAcceptsInputImages(model) -> ImageModelCapabilities(
                inputMode = ImageInputMode.OPTIONAL,
                maxInputImages = 3,
                supportsProgressivePreview = false,
            )
            else -> ImageModelCapabilities(
                inputMode = ImageInputMode.NONE,
                maxInputImages = 0,
                supportsProgressivePreview = false,
            )
        }
    }

    if (ModelRequestPolicy.isOfficialOpenAi(provider)) {
        val id = model.modelId.substringAfterLast('/').lowercase()
        val editable = id.startsWith("gpt-image-") || id == "chatgpt-image-latest" || id == "dall-e-2"
        return ImageModelCapabilities(
            inputMode = if (editable) ImageInputMode.OPTIONAL else ImageInputMode.NONE,
            maxInputImages = if (editable) 16 else 0,
            supportsProgressivePreview = id.startsWith("gpt-image-") || id == "chatgpt-image-latest",
        )
    }

    // Other OpenAI-compatible image endpoints are intentionally conservative.
    // Xylune cannot infer an edit protocol or partial-image stream from a generic
    // supportsImageGeneration flag alone.
    return ImageModelCapabilities(
        inputMode = ImageInputMode.NONE,
        maxInputImages = 0,
        supportsProgressivePreview = false,
    )
}

fun imageModelActionLabel(provider: ProviderEntity?, model: ModelEntity?): String {
    val capabilities = imageModelCapabilities(provider, model) ?: return "Image"
    return when (capabilities.inputMode) {
        ImageInputMode.REQUIRED -> "Edit"
        ImageInputMode.OPTIONAL -> "Generate + edit"
        ImageInputMode.NONE -> "Generate"
    }
}
