#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content)


def replace_once(path: str, old: str, new: str) -> None:
    content = read(path)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:120]!r}")
    write(path, content.replace(old, new, 1))


# Version.
replace_once("app/build.gradle.kts", "versionCode = 165", "versionCode = 166")
replace_once("app/build.gradle.kts", 'versionName = "0.22.1"', 'versionName = "0.22.2"')

# All app-owned predictive navigation must defer to the IME while it is visible.
predictive = "app/src/main/java/app/arbor/chat/ui/PredictiveNavigation.kt"
replace_once(
    predictive,
    "import androidx.compose.foundation.layout.fillMaxSize\n",
    "import androidx.compose.foundation.layout.WindowInsets\nimport androidx.compose.foundation.layout.fillMaxSize\nimport androidx.compose.foundation.layout.ime\n",
)
replace_once(
    predictive,
    "import androidx.compose.ui.Modifier\n",
    "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalDensity\n",
)
replace_once(
    predictive,
    "private const val CommitFadeStart = 0.62f\n\n\ninternal fun predictiveBackCompletionDurationMillis",
    "private const val CommitFadeStart = 0.62f\n\ninternal fun appBackHandlerEnabled(ownerEnabled: Boolean, imeVisible: Boolean): Boolean =\n    ownerEnabled && !imeVisible\n\ninternal fun predictiveBackCompletionDurationMillis",
)
replace_once(
    predictive,
    "    @Suppress(\"UNUSED_VARIABLE\")\n    val inspectorLabel = label\n\n    val progress = remember { Animatable(0f) }",
    "    @Suppress(\"UNUSED_VARIABLE\")\n    val inspectorLabel = label\n    val density = LocalDensity.current\n    val imeVisible = WindowInsets.ime.getBottom(density) > 0\n\n    val progress = remember { Animatable(0f) }",
)
replace_once(
    predictive,
    "        enabled = backEnabled && backTarget != null && mode != NavigationTransitionMode.ORDINARY,\n",
    "        enabled = appBackHandlerEnabled(\n            ownerEnabled = backEnabled && backTarget != null && mode != NavigationTransitionMode.ORDINARY,\n            imeVisible = imeVisible,\n        ),\n",
)

app = "app/src/main/java/app/arbor/chat/ui/ArborApp.kt"
replace_once(
    app,
    "import androidx.compose.foundation.layout.fillMaxSize\n",
    "import androidx.compose.foundation.layout.fillMaxSize\nimport androidx.compose.foundation.layout.ime\n",
)
replace_once(
    app,
    "import androidx.compose.ui.Modifier\n",
    "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalDensity\n",
)
replace_once(
    app,
    "    val drawerState = rememberInteractiveDrawerState()\n    val snackbar = remember { SnackbarHostState() }",
    "    val drawerState = rememberInteractiveDrawerState()\n    val density = LocalDensity.current\n    val imeVisible = WindowInsets.ime.getBottom(density) > 0\n    val snackbar = remember { SnackbarHostState() }",
)
replace_once(
    app,
    "    PredictiveBackHandler(enabled = drawerClaimsBack) { events ->",
    "    PredictiveBackHandler(\n        enabled = appBackHandlerEnabled(drawerClaimsBack, imeVisible),\n    ) { events ->",
)
replace_once(
    app,
    "                backEnabled = pageBackEnabled(drawerClaimsBack),",
    "                backEnabled = pageBackEnabled(drawerClaimsBack, imeVisible),",
)
replace_once(
    app,
    "internal fun pageBackEnabled(drawerVisible: Boolean): Boolean = !drawerVisible\n",
    "internal fun pageBackEnabled(drawerVisible: Boolean, imeVisible: Boolean = false): Boolean =\n    !drawerVisible && !imeVisible\n",
)

# Search becomes compact, pinned, and IME-aware instead of putting the field
# below an oversized collapsing title inside the scrolling result list.
search = "app/src/main/java/app/arbor/chat/ui/SearchScreen.kt"
write(search, '''package app.arbor.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateTopPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
                title = { Text("Search history") },
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
                .padding(top = padding.calculateTopPadding())
                .imePadding(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.searchQuery.value = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Outlined.Clear, "Clear search")
                        }
                    }
                } else null,
                placeholder = { Text("Search messages, code, and reasoning") },
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
                                    result.snippet.replace("[", "").replace("]", ""),
                                    maxLines = 3,
                                )
                            },
                            supportingContent = {
                                Text(
                                    result.conversationTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            androidx.compose.foundation.layout.Spacer(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 0.dp)
                                    .then(
                                        Modifier,
                                    ),
                            )
                        }
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                        ) {
                            androidx.compose.foundation.layout.Box(
                                Modifier
                                    .matchParentSize()
                                    .then(
                                        Modifier,
                                    ),
                            )
                        }
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
''')

# Give app-bar collapse one owner. The list/scroll state already synchronizes
# TopAppBarState; attaching Material's nested-scroll connection as well made the
# bar consume pixels before the list and then snap back when list offset began.
chat = "app/src/main/java/app/arbor/chat/ui/ChatScreen.kt"
replace_once(
    chat,
    "    Scaffold(\n        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),",
    "    Scaffold(\n        modifier = Modifier.fillMaxSize(),",
)

settings = "app/src/main/java/app/arbor/chat/ui/SettingsScreen.kt"
replace_once(
    settings,
    "        Scaffold(\n            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),",
    "        Scaffold(\n            modifier = Modifier.fillMaxSize(),",
)
replace_once(
    settings,
    "                .collect { (offset, limit) ->\n                    state.heightOffset = settingsTopBarHeightOffset(offset, limit)\n                    state.contentOffset = -offset.coerceAtLeast(0).toFloat()\n                }",
    "                .collect { (offset, limit) ->\n                    // A zero limit is a transient pre-measure/resize state. Writing\n                    // heightOffset then would expand an already-collapsed title.\n                    if (limit < 0f) {\n                        state.heightOffset = settingsTopBarHeightOffset(offset, limit)\n                    }\n                    state.contentOffset = -offset.coerceAtLeast(0).toFloat()\n                }",
)

# Parse returned Google authorization data before trusting resultCode. Some
# account-selection resolutions return usable data with RESULT_CANCELED.
cloud = "app/src/main/java/app/arbor/chat/ui/CloudBackupUi.kt"
replace_once(
    cloud,
    "private sealed interface GoogleBackupAction {\n",
    "internal enum class GoogleAuthorizationResultRoute {\n    PARSE_RESULT,\n    CANCELLED,\n    MISSING_RESULT,\n}\n\ninternal fun googleAuthorizationResultRoute(resultCode: Int, hasData: Boolean): GoogleAuthorizationResultRoute =\n    when {\n        hasData -> GoogleAuthorizationResultRoute.PARSE_RESULT\n        resultCode == Activity.RESULT_CANCELED -> GoogleAuthorizationResultRoute.CANCELLED\n        else -> GoogleAuthorizationResultRoute.MISSING_RESULT\n    }\n\nprivate sealed interface GoogleBackupAction {\n",
)
replace_once(
    cloud,
    '''        if (result.resultCode != Activity.RESULT_OK) {
            driveError = "Google Drive connection was canceled."
            return@rememberLauncherForActivityResult
        }
        runCatching { authorizationClient.getAuthorizationResultFromIntent(result.data ?: Intent()) }
            .onSuccess { acceptAuthorization(action, it) }
            .onFailure { driveError = it.message ?: "Google Drive authorization failed" }
''',
    '''        val data = result.data
        when (googleAuthorizationResultRoute(result.resultCode, data != null)) {
            GoogleAuthorizationResultRoute.PARSE_RESULT -> {
                runCatching { authorizationClient.getAuthorizationResultFromIntent(requireNotNull(data)) }
                    .onSuccess { acceptAuthorization(action, it) }
                    .onFailure { error ->
                        driveError = error.message ?: "Google Drive authorization failed"
                    }
            }
            GoogleAuthorizationResultRoute.CANCELLED -> {
                driveError = "Google Drive connection was canceled."
            }
            GoogleAuthorizationResultRoute.MISSING_RESULT -> {
                driveError = "Google Drive authorization returned no result (code ${result.resultCode})."
            }
        }
''',
)

# Regression coverage.
test = ROOT / "app/src/test/java/app/arbor/chat/ui/KeyboardSearchDriveRegressionTest.kt"
test.write_text('''package app.arbor.chat.ui

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSearchDriveRegressionTest {
    @Test
    fun keyboardOwnsBackBeforeDrawerOrPageNavigation() {
        assertFalse(appBackHandlerEnabled(ownerEnabled = true, imeVisible = true))
        assertTrue(appBackHandlerEnabled(ownerEnabled = true, imeVisible = false))
        assertFalse(pageBackEnabled(drawerVisible = false, imeVisible = true))
        assertFalse(pageBackEnabled(drawerVisible = true, imeVisible = false))
        assertTrue(pageBackEnabled(drawerVisible = false, imeVisible = false))
    }

    @Test
    fun authorizationDataWinsOverCanceledResultCode() {
        assertEquals(
            GoogleAuthorizationResultRoute.PARSE_RESULT,
            googleAuthorizationResultRoute(Activity.RESULT_CANCELED, hasData = true),
        )
        assertEquals(
            GoogleAuthorizationResultRoute.CANCELLED,
            googleAuthorizationResultRoute(Activity.RESULT_CANCELED, hasData = false),
        )
        assertEquals(
            GoogleAuthorizationResultRoute.MISSING_RESULT,
            googleAuthorizationResultRoute(Activity.RESULT_OK, hasData = false),
        )
    }

    @Test
    fun searchUsesCompactPinnedImeAwareLayout() {
        val source = java.io.File("src/main/java/app/arbor/chat/ui/SearchScreen.kt").readText()
        assertFalse(source.contains("CollapsingTranslucentTopBar"))
        assertFalse(source.contains("LargeTopAppBar"))
        assertTrue(source.contains("TopAppBar("))
        assertTrue(source.contains(".imePadding()"))
        assertTrue(source.indexOf("OutlinedTextField(") < source.indexOf("LazyColumn("))
    }

    @Test
    fun synchronizedTopBarsDoNotAlsoConsumeNestedScroll() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        assertFalse(chat.contains("Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)"))
        assertFalse(settings.contains("Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)"))
        assertTrue(settings.contains("if (limit < 0f)"))
    }

    @Test
    fun fullCollapseBoundaryRemainsCollapsed() {
        assertEquals(1f, calculateTopChromeProgress(0, 176, 56, 176), 0f)
        assertEquals(1f, calculateTopChromeProgress(1, 0, 56, 176), 0f)
    }
}
''')

notes = "docs/releases/RELEASE_NOTES_0.22.2.md"
write(notes, '''# Arbor 0.22.2

- Give the Android IME first ownership of predictive Back while the keyboard is visible.
- Prevent drawer and page navigation from consuming the keyboard's Back gesture.
- Replace Search's oversized collapsing header with a compact, pinned, IME-aware search field and clear empty states.
- Remove competing nested-scroll ownership that made fully collapsed chat and Settings titles snap back to expanded.
- Keep a collapsed Settings title stable through transient pre-measure and IME-resize states.
- Parse Google authorization result data before classifying the account picker as canceled.
''')

print("Applied Arbor 0.22.2 keyboard, search, collapse, and Drive patch")
