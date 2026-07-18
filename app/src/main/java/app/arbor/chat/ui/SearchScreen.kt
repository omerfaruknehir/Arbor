package app.arbor.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: ChatViewModel, openDrawer: (() -> Unit)?) {
    val chromeBlurEnabled by viewModel.chromeBlurEnabled.collectAsState()
    val chromeBlurStrength by viewModel.chromeBlurStrength.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val blurState = rememberArborBackdropBlurState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CollapsingTranslucentTopBar(
                title = "Search history",
                scrollBehavior = scrollBehavior,
                blurState = blurState,
                blurEnabled = chromeBlurEnabled,
                blurStrength = chromeBlurStrength,
                navigationIcon = {
                    IconButton(onClick = { openDrawer?.invoke() ?: run { viewModel.screen.value = Screen.CHAT } }) {
                        Icon(if (openDrawer != null) Icons.Outlined.Menu else Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().arborBackdropSource(blurState),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 16.dp,
                bottom = 16.dp,
            ),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.searchQuery.value = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Search messages, code, and reasoning") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(results, key = { "${it.conversationId}:${it.nodeId}:${it.rank}:${it.snippet.hashCode()}" }) { result ->
                ListItem(
                    headlineContent = { Text(result.snippet.replace("[", "").replace("]", ""), maxLines = 3) },
                    supportingContent = { Text(result.conversationTitle, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.clickable { viewModel.openSearchResult(result.conversationId, result.nodeId) },
                )
            }
        }
    }
}
