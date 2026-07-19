package app.arbor.chat.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ArborBlurEdge { TOP, BOTTOM }

/**
 * Shared configuration for the gradual blur applied to a screen's actual body.
 *
 * Scroll progress is retained as a reader instead of copied into Compose state.
 * The source layer therefore invalidates only its draw/layer phase while
 * scrolling; it does not rebuild shaders or recompose the whole screen per pixel.
 */
@Stable
class ArborBackdropBlurState internal constructor() {
    internal var topRadiusScaleDp by mutableFloatStateOf(0f)
    internal var bottomRadiusScaleDp by mutableFloatStateOf(0f)
    internal var topFadeDp by mutableFloatStateOf(DEFAULT_TOP_FADE_DP)
    internal var bottomFadeDp by mutableFloatStateOf(DEFAULT_BOTTOM_FADE_DP)
    internal var topProgressReader by mutableStateOf<() -> Float>(ZERO_PROGRESS_READER)
    internal var bottomProgressReader by mutableStateOf<() -> Float>(ZERO_PROGRESS_READER)
    internal var sourceTopInRootPx by mutableFloatStateOf(0f)
    internal var bottomEdgeInRootPx by mutableFloatStateOf(Float.NaN)

    internal fun update(
        edge: ArborBlurEdge,
        radiusScaleDp: Float,
        fadeDp: Float,
        progressReader: () -> Float,
    ) {
        when (edge) {
            ArborBlurEdge.TOP -> {
                topRadiusScaleDp = radiusScaleDp.coerceAtLeast(0f)
                topFadeDp = fadeDp.coerceAtLeast(1f)
                if (topProgressReader !== progressReader) topProgressReader = progressReader
            }
            ArborBlurEdge.BOTTOM -> {
                bottomRadiusScaleDp = radiusScaleDp.coerceAtLeast(0f)
                bottomFadeDp = fadeDp.coerceAtLeast(1f)
                if (bottomProgressReader !== progressReader) bottomProgressReader = progressReader
            }
        }
    }

    internal fun updateSource(topInRootPx: Float) {
        sourceTopInRootPx = topInRootPx
    }

    internal fun updateBottomEdge(bottomInRootPx: Float) {
        bottomEdgeInRootPx = bottomInRootPx
    }

    internal fun clear(edge: ArborBlurEdge) {
        when (edge) {
            ArborBlurEdge.TOP -> {
                topRadiusScaleDp = 0f
                topProgressReader = ZERO_PROGRESS_READER
            }
            ArborBlurEdge.BOTTOM -> {
                bottomRadiusScaleDp = 0f
                bottomProgressReader = ZERO_PROGRESS_READER
                bottomEdgeInRootPx = Float.NaN
            }
        }
    }

    private companion object {
        val ZERO_PROGRESS_READER: () -> Float = { 0f }
    }
}

@Composable
fun rememberArborBackdropBlurState(): ArborBackdropBlurState = remember { ArborBackdropBlurState() }

/** Applies a spatially gradual blur directly to the scrolling content layer. */
fun Modifier.arborBackdropSource(state: ArborBackdropBlurState): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@composed this

    val density = LocalDensity.current.density
    val topRadiusScalePx = state.topRadiusScaleDp * density
    val bottomRadiusScalePx = state.bottomRadiusScaleDp * density
    val topFadePx = state.topFadeDp * density
    val bottomFadePx = state.bottomFadeDp * density
    val topProgressReader = state.topProgressReader
    val bottomProgressReader = state.bottomProgressReader

    var contentHeightPx by remember { mutableFloatStateOf(0f) }
    val measured = this.onGloballyPositioned { coordinates ->
        contentHeightPx = coordinates.size.height.toFloat().coerceAtLeast(1f)
        state.updateSource(coordinates.boundsInRoot().top)
    }
    if (contentHeightPx <= 0f) return@composed measured

    val bottomEdgePx = state.bottomEdgeInRootPx
        .takeIf { it.isFinite() }
        ?.minus(state.sourceTopInRootPx)
        ?.coerceIn(0f, contentHeightPx)
        ?: contentHeightPx

    val horizontalShader = remember {
        RuntimeShader(EDGE_BLUR_SHADER).apply {
            setFloatUniform("uDirection", 1f, 0f)
        }
    }
    val verticalShader = remember {
        RuntimeShader(EDGE_BLUR_SHADER).apply {
            setFloatUniform("uDirection", 0f, 1f)
        }
    }
    val composeEffect = remember(horizontalShader, verticalShader) {
        val horizontal = RenderEffect.createRuntimeShaderEffect(horizontalShader, "content")
        val vertical = RenderEffect.createRuntimeShaderEffect(verticalShader, "content")
        RenderEffect.createChainEffect(vertical, horizontal).asComposeRenderEffect()
    }

    measured.graphicsLayer {
        val topRadiusPx = topRadiusScalePx * arborBlurProgress(topProgressReader())
        val bottomRadiusPx = bottomRadiusScalePx * arborBlurProgress(bottomProgressReader())
        if (topRadiusPx < MIN_VISIBLE_RADIUS_PX && bottomRadiusPx < MIN_VISIBLE_RADIUS_PX) {
            renderEffect = null
            return@graphicsLayer
        }

        horizontalShader.setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
        horizontalShader.setFloatUniform("uFade", topFadePx, bottomFadePx)
        horizontalShader.setFloatUniform("uHeight", contentHeightPx.coerceAtLeast(1f))
        horizontalShader.setFloatUniform("uBottomEdge", bottomEdgePx)

        verticalShader.setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
        verticalShader.setFloatUniform("uFade", topFadePx, bottomFadePx)
        verticalShader.setFloatUniform("uHeight", contentHeightPx.coerceAtLeast(1f))
        verticalShader.setFloatUniform("uBottomEdge", bottomEdgePx)

        renderEffect = composeEffect
    }
}

internal fun arborBlurProgress(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return p * p * (3f - 2f * p)
}

/** Registers one chrome edge and paints a directional, gradually fading tint. */
fun Modifier.arborBackdropBlur(
    state: ArborBackdropBlurState,
    enabled: Boolean,
    progress: () -> Float,
    strength: Float,
    tint: Color,
    edge: ArborBlurEdge = ArborBlurEdge.TOP,
    maxRadius: Dp = DEFAULT_MAX_RADIUS_DP.dp,
    fadeDistance: Dp = if (edge == ArborBlurEdge.TOP) DEFAULT_TOP_FADE_DP.dp else DEFAULT_BOTTOM_FADE_DP.dp,
    overlayDistance: Dp = fadeDistance,
): Modifier = composed {
    val latestProgress by rememberUpdatedState(progress)
    val stableProgressReader = remember { { latestProgress().coerceIn(0f, 1f) } }
    val radiusScaleDp = if (enabled) {
        maxRadius.value * strength.coerceIn(0f, 1f)
    } else {
        0f
    }

    SideEffect {
        state.update(
            edge = edge,
            radiusScaleDp = radiusScaleDp,
            fadeDp = fadeDistance.value,
            progressReader = stableProgressReader,
        )
    }
    DisposableEffect(state, edge) { onDispose { state.clear(edge) } }

    val anchored = if (edge == ArborBlurEdge.BOTTOM) {
        this.onGloballyPositioned { coordinates ->
            state.updateBottomEdge(coordinates.boundsInRoot().bottom)
        }
    } else {
        this
    }

    val overlayDistancePx = with(LocalDensity.current) { overlayDistance.toPx() }

    anchored.drawWithCache {
        val extent = overlayDistancePx.coerceIn(1f, size.height.coerceAtLeast(1f))
        val brush = when (edge) {
            ArborBlurEdge.TOP -> Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to tint,
                    0.30f to tint.copy(alpha = tint.alpha * 0.58f),
                    0.62f to tint.copy(alpha = tint.alpha * 0.12f),
                    1f to Color.Transparent,
                ),
                startY = 0f,
                endY = extent,
            )
            ArborBlurEdge.BOTTOM -> {
                val startY = (size.height - extent).coerceAtLeast(0f)
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.38f to tint.copy(alpha = tint.alpha * 0.12f),
                        0.70f to tint.copy(alpha = tint.alpha * 0.58f),
                        1f to tint,
                    ),
                    startY = startY,
                    endY = size.height,
                )
            }
        }

        onDrawWithContent {
            val overlayProgress = if (enabled) arborBlurProgress(stableProgressReader()) else 1f
            when (edge) {
                ArborBlurEdge.TOP -> drawRect(
                    brush = brush,
                    alpha = overlayProgress,
                    size = androidx.compose.ui.geometry.Size(size.width, extent),
                )
                ArborBlurEdge.BOTTOM -> {
                    val startY = (size.height - extent).coerceAtLeast(0f)
                    drawRect(
                        brush = brush,
                        alpha = overlayProgress,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, startY),
                        size = androidx.compose.ui.geometry.Size(size.width, extent),
                    )
                }
            }
            drawContent()
        }
    }
}

private const val MIN_VISIBLE_RADIUS_PX = 0.35f
private const val DEFAULT_MAX_RADIUS_DP = 24f
private const val DEFAULT_TOP_FADE_DP = 64f
private const val DEFAULT_BOTTOM_FADE_DP = 152f

/**
 * Restored 0.16.19 two-pass seventeen-tap Gaussian kernel.
 *
 * The uniforms are updated from the graphics layer rather than Compose state,
 * so the previous blur appearance is retained without recomposing the screen
 * for each pixel of app-bar movement.
 */
private val EDGE_BLUR_SHADER = """
    uniform shader content;
    uniform float2 uBlur;
    uniform float2 uFade;
    uniform float uHeight;
    uniform float uBottomEdge;
    uniform float2 uDirection;

    half4 main(float2 coord) {
        float topMix = saturate(1.0 - coord.y / max(uFade.x, 1.0));
        float bottomEdge = clamp(uBottomEdge, 0.0, uHeight);
        float bottomMix = saturate(1.0 - (bottomEdge - coord.y) / max(uFade.y, 1.0));
        float radius = max(uBlur.x * topMix, uBlur.y * bottomMix);
        if (radius < 0.35) return content.eval(coord);

        float2 sampleStep = uDirection * (radius / 8.0);
        half4 accum = half4(content.eval(coord)) * 0.103152619;
        accum += half4(content.eval(coord + sampleStep * 1.0)) * 0.099978946;
        accum += half4(content.eval(coord - sampleStep * 1.0)) * 0.099978946;
        accum += half4(content.eval(coord + sampleStep * 2.0)) * 0.091031867;
        accum += half4(content.eval(coord - sampleStep * 2.0)) * 0.091031867;
        accum += half4(content.eval(coord + sampleStep * 3.0)) * 0.077863682;
        accum += half4(content.eval(coord - sampleStep * 3.0)) * 0.077863682;
        accum += half4(content.eval(coord + sampleStep * 4.0)) * 0.062565226;
        accum += half4(content.eval(coord - sampleStep * 4.0)) * 0.062565226;
        accum += half4(content.eval(coord + sampleStep * 5.0)) * 0.047226710;
        accum += half4(content.eval(coord - sampleStep * 5.0)) * 0.047226710;
        accum += half4(content.eval(coord + sampleStep * 6.0)) * 0.033488752;
        accum += half4(content.eval(coord - sampleStep * 6.0)) * 0.033488752;
        accum += half4(content.eval(coord + sampleStep * 7.0)) * 0.022308318;
        accum += half4(content.eval(coord - sampleStep * 7.0)) * 0.022308318;
        accum += half4(content.eval(coord + sampleStep * 8.0)) * 0.013960189;
        accum += half4(content.eval(coord - sampleStep * 8.0)) * 0.013960189;
        return accum;
    }
""".trimIndent()
