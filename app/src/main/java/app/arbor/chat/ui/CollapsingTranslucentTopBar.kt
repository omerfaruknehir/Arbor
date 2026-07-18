package app.arbor.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingTranslucentTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val collapse = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val blurRadius = (6f + 14f * collapse).dp
    val topAlpha = 0.80f + 0.16f * collapse
    val middleAlpha = 0.38f + 0.36f * collapse
    val bottomAlpha = 0.05f + 0.23f * collapse
    val appBarHeight = (152f - 88f * collapse).dp

    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(appBarHeight)
                .blur(blurRadius)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = topAlpha),
                            MaterialTheme.colorScheme.surface.copy(alpha = middleAlpha),
                            MaterialTheme.colorScheme.surface.copy(alpha = bottomAlpha),
                        ),
                    ),
                ),
        )
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
