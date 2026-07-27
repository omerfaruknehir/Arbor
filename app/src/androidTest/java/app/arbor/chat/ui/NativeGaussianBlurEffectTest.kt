package app.arbor.chat.ui

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class NativeGaussianBlurEffectTest {
    @Test
    fun topAndBottomNativeGraphsConstructWithoutRuntimeShaderImageFilterChaining() {
        listOf(ArborBlurEdge.TOP, ArborBlurEdge.BOTTOM).forEach { edge ->
            assertNotNull(
                buildNativeGaussianPanelEffect(
                    edge = edge,
                    radiusPx = 48f,
                    startPx = if (edge == ArborBlurEdge.TOP) 0f else 1600f,
                    endPx = if (edge == ArborBlurEdge.TOP) 240f else 1920f,
                    contentWidthPx = 1080f,
                    contentHeightPx = 1920f,
                    density = 3f,
                    softness = .55f,
                    cornerRadiusDp = 0f,
                    mergeDp = 42f,
                    saturation = 1.1f,
                    contrast = 1.025f,
                    brightness = 1.008f,
                ),
            )
        }
    }
}
