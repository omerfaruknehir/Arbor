package app.arbor.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: ChatViewModel, openDrawer: (() -> Unit)?) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Search history") },
                navigationIcon = { IconButton(onClick = { openDrawer?.invoke() ?: run { viewModel.screen.value = Screen.CHAT } }) { Icon(if (openDrawer != null) Icons.Outlined.Menu else Icons.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                query, { viewModel.searchQuery.value = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Search messages, code, and reasoning") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            LazyColumn {
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
}
