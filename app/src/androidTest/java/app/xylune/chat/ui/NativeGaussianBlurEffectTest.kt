package app.xylune.chat.ui

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
    fun directThreeAxisRuntimeShaderGraphConstructsOnDevice() {
        assertNotNull(
            buildPanelEdgeBlurEffect(
                topRadiusPx = 48f,
                bottomRadiusPx = 36f,
                topStartPx = 0f,
                topEndPx = 240f,
                bottomStartPx = 1600f,
                bottomEndPx = 1920f,
                contentWidthPx = 1080f,
                contentHeightPx = 1920f,
                density = 3f,
                topCornerRadiusDp = 0f,
                bottomCornerRadiusDp = 0f,
                topMergeDp = 42f,
                bottomMergeDp = 42f,
            ),
        )
    }
}
