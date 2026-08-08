package app.xylune.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: ChatViewModel, openDrawer: (() -> Unit)?) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(uiText("Search history")) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            openDrawer?.invoke() ?: run { viewModel.screen.value = Screen.CHAT }
                        },
                    ) {
                        Icon(
                            if (openDrawer != null) Icons.Outlined.Menu else Icons.AutoMirrored.Outlined.ArrowBack,
                            if (openDrawer != null) "Open navigation drawer" else "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.searchQuery.value = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Outlined.Clear, uiText("Clear search"))
                        }
                    }
                } else null,
                placeholder = { Text(uiText("Search messages, code, and reasoning")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .focusRequester(focusRequester),
            )
            HorizontalDivider()

            when {
                query.isBlank() -> SearchEmptyState(
                    title = "Search across every chat",
                    body = "Messages, code, and reasoning are searched locally as you type.",
                )
                results.isEmpty() -> SearchEmptyState(
                    title = "No matches",
                    body = "Try a different word or a shorter phrase.",
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(
                        items = results,
                        key = { "${it.conversationId}:${it.nodeId}:${it.rank}:${it.snippet.hashCode()}" },
                    ) { result ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    uiText(result.snippet.replace("[", "").replace("]", "")),
                                    maxLines = 3,
                                )
                            },
                            supportingContent = {
                                Text(
                                    result.conversationTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusManager.clearFocus()
                                    viewModel.openSearchResult(result.conversationId, result.nodeId)
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
