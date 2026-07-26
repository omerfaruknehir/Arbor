package app.arbor.chat

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.arbor.chat.ui.ArborApp
import app.arbor.chat.ui.ChatViewModel
import app.arbor.chat.ui.theme.ArborTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels {
        ChatViewModel.factory((application as ArborApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val amoled by viewModel.amoled.collectAsState()
            val palette by viewModel.palette.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()
            ArborTheme(amoled = amoled, palette = palette, themeMode = themeMode) {
                val appName = stringResource(R.string.app_name)
                ArborApp(viewModel, this@MainActivity)
                val container = (application as ArborApplication).container
                var crashReport by remember { mutableStateOf(container.crashReporter.read()) }
                val renderSafeMode by viewModel.renderSafeMode.collectAsState()
                crashReport?.let { report ->
                    val context = LocalContext.current
                    AlertDialog(
                        onDismissRequest = { container.crashReporter.clear(); crashReport = null },
                        title = { Text("$appName recovered a crash report") },
                        text = {
                            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                if (renderSafeMode) {
                                    Text("$appName reopened safely with generated widgets paused. Your chats and files were not deleted. You can dismiss this report and keep using the app, then retry full rendering when ready.\n")
                                    OutlinedButton(onClick = { viewModel.setRenderSafeMode(false) }) { Text("Try full rendering again") }
                                    Text("\n")
                                }
                                Text("Copy this redacted diagnostic report if you need help diagnosing the failure. Review it before sharing.\n\n$report")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { container.crashReporter.clear(); crashReport = null }) { Text("Dismiss") }
                        },
                        confirmButton = {
                            Button(onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("$appName crash report", report))
                            }) { Text("Copy report") }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            else -> emptyList()
        }
        viewModel.receiveIntent(intent.getStringExtra(EXTRA_CONVERSATION_ID), uris)
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : Parcelable> Intent.parcelableExtra(key: String): T? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else getParcelableExtra(key)

    @Suppress("DEPRECATION")
    private inline fun <reified T : Parcelable> Intent.parcelableArrayListExtra(key: String): List<T> =
        if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(key, T::class.java).orEmpty() else getParcelableArrayListExtra<T>(key).orEmpty()

    companion object { const val EXTRA_CONVERSATION_ID = "conversation_id" }
}
