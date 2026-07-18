package app.arbor.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * A collapsing app bar with genuine backdrop blur. The background tint remains
 * constant while the blur radius grows smoothly with the collapse fraction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingTranslucentTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    blurState: ArborBackdropBlurState,
    blurEnabled: Boolean = true,
    blurStrength: Float = 0.7f,
) {
    val collapse = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .arborBackdropBlur(
                state = blurState,
                enabled = blurEnabled,
                progress = collapse,
                strength = blurStrength,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.24f),
            ),
    ) {
        LargeTopAppBar(
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }
}
