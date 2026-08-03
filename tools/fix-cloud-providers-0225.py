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
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:140]!r}")
    write(path, content.replace(old, new, 1))


clients = "app/src/main/java/app/arbor/chat/transfer/DirectCloudBackupClients.kt"
replace_once(clients, '.addQueryParameter("$select", "id,name,size,lastModifiedDateTime,file")', '.addQueryParameter("\\$select", "id,name,size,lastModifiedDateTime,file")')
replace_once(clients, '.addQueryParameter("$orderby", "lastModifiedDateTime desc")', '.addQueryParameter("\\$orderby", "lastModifiedDateTime desc")')
replace_once(clients, '.addQueryParameter("$top", "100")', '.addQueryParameter("\\$top", "100")')
replace_once(
    clients,
    '''        val canonicalQuery = url.queryParameterNames.sorted().flatMap { name ->
            url.queryParameterValues(name).sorted().map { value ->
                "${awsEncode(name)}=${awsEncode(value ?: "")}" 
            }
        }.joinToString("&")
''',
    '''        val canonicalQuery = url.queryParameterNames.sorted().flatMap { name ->
            url.queryParameterValues(name)
                .map { value -> value.orEmpty() }
                .sorted()
                .map { value -> "${awsEncode(name)}=${awsEncode(value)}" }
        }.joinToString("&")
''',
)

setup = "app/src/main/java/app/arbor/chat/ui/SetupRestoreUi.kt"
replace_once(
    setup,
    '''                                                    CloudBackupProvider.GOOGLE_DRIVE_APP_DATA -> {
                                                        val token = requireNotNull(googleAccessToken) { "Google Drive authorization expired" }
                                                        viewModel.downloadGoogleDriveBackup(token, entry)
                                                    }
                                                }
''',
    '''                                                    CloudBackupProvider.GOOGLE_DRIVE_APP_DATA -> {
                                                        val token = requireNotNull(googleAccessToken) { "Google Drive authorization expired" }
                                                        viewModel.downloadGoogleDriveBackup(token, entry)
                                                    }
                                                    else -> error("This cloud provider is not available from first-run restore yet")
                                                }
''',
)

ui = "app/src/main/java/app/arbor/chat/ui/DirectCloudProvidersUi.kt"
replace_once(
    ui,
    "import androidx.compose.foundation.layout.Column\n",
    "import androidx.compose.foundation.layout.Column\nimport androidx.compose.foundation.layout.ColumnScope\n",
)
replace_once(ui, "import androidx.compose.foundation.text.KeyboardOptions\n", "")
replace_once(ui, "import androidx.compose.ui.text.input.KeyboardType\n", "")
replace_once(ui, "import androidx.core.net.toUri\n", "")
replace_once(
    ui,
    '''            runCatching { viewModel.writeDirectCloudBackup(provider, options, password) }
                .onSuccess {
                    viewModel.postNotice("Backup saved to ${provider.displayName}")
                    entries = entries.toMutableMap().apply {
                        put(provider, viewModel.listDirectCloudBackups(provider))
                    }
                }
                .onFailure { setError(provider, it.message ?: "Cloud backup failed") }
''',
    '''            runCatching {
                viewModel.writeDirectCloudBackup(provider, options, password)
                viewModel.listDirectCloudBackups(provider)
            }
                .onSuccess { refreshed ->
                    viewModel.postNotice("Backup saved to ${provider.displayName}")
                    entries = entries.toMutableMap().apply { put(provider, refreshed) }
                }
                .onFailure { setError(provider, it.message ?: "Cloud backup failed") }
''',
)
replace_once(
    ui,
    "    content: @Composable Column.() -> Unit,",
    "    content: @Composable ColumnScope.() -> Unit,",
)

# JUnit 4 has assertThrows; assertFailsWith belongs to kotlin-test.
test = "app/src/test/java/app/arbor/chat/transfer/DirectCloudConfigurationTest.kt"
replace_once(test, "import org.junit.Assert.assertFailsWith\n", "import org.junit.Assert.assertThrows\n")
replace_once(test, "        assertFailsWith<IllegalArgumentException> {\n            validateWebDavConfig(WebDavCloudConfig(\"Bad\", \"http://cloud.example/dav\", \"u\", \"p\"))\n        }", "        assertThrows(IllegalArgumentException::class.java) {\n            validateWebDavConfig(WebDavCloudConfig(\"Bad\", \"http://cloud.example/dav\", \"u\", \"p\"))\n        }")
replace_once(test, "        assertFailsWith<IllegalArgumentException> {\n            validateS3Config(value.copy(secretAccessKey = \"\"))\n        }", "        assertThrows(IllegalArgumentException::class.java) {\n            validateS3Config(value.copy(secretAccessKey = \"\"))\n        }")
replace_once(
    test,
    'assertTrue(manifest.contains("android:scheme=\\"${dropboxOAuthScheme}\\""))',
    'assertTrue(manifest.contains("android:scheme=\\"\\${dropboxOAuthScheme}\\""))',
)

print("Corrected Arbor 0.22.5 cloud provider compile issues")
