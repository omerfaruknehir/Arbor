package app.xylune.chat

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.app.LocaleManager
import android.content.Context
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import app.xylune.chat.ui.XyluneApp
import app.xylune.chat.ui.LocalXyluneIconPalette
import app.xylune.chat.ui.LocalXyluneUiLanguage
import app.xylune.chat.ui.resolvedUiLanguage
import app.xylune.chat.ui.uiText
import app.xylune.chat.ui.ChatViewModel
import app.xylune.chat.ui.theme.XyluneTheme
import app.xylune.chat.ui.theme.resolvedXyluneColorScheme
import app.xylune.chat.settings.ColorPalette
import app.xylune.chat.settings.AppLanguage
import app.xylune.chat.settings.withStoredXyluneLanguage
import app.xylune.chat.settings.LauncherIconManager
import app.xylune.chat.transfer.XYLUNE_BACKUP_EXTENSION
import app.xylune.chat.transfer.XYLUNE_BACKUP_MIME
import app.xylune.chat.transfer.XYLUNE_CHAT_EXTENSION
import app.xylune.chat.transfer.XYLUNE_CHAT_MIME
import kotlinx.coroutines.launch

import app.xylune.chat.ui.XyluneAlertDialog
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withStoredXyluneLanguage())
    }

    private var launcherRestartInFlight = false
    private val viewModel: ChatViewModel by viewModels {
        ChatViewModel.factory((application as XyluneApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = (application as XyluneApplication).container.appPreferences
        if (Build.VERSION.SDK_INT >= 33) {
            val localeManager = getSystemService(LocaleManager::class.java)
            val platformLanguage = if (localeManager.applicationLocales.isEmpty) {
                AppLanguage.SYSTEM
            } else {
                when (localeManager.applicationLocales[0].language) {
                    "tr" -> AppLanguage.TURKISH
                    "en" -> AppLanguage.ENGLISH
                    else -> AppLanguage.SYSTEM
                }
            }
            if (platformLanguage != preferences.appLanguage.value) preferences.setAppLanguage(platformLanguage)
        }
        window.setBackgroundDrawable(
            ColorDrawable(
                resolvedXyluneColorScheme(
                    context = this,
                    palette = preferences.palette.value,
                    themeMode = preferences.themeMode.value,
                    amoled = preferences.amoled.value,
                ).background.toArgb(),
            ),
        )
        enableEdgeToEdge()
        handleIntent(intent)
        observeLauncherRestarts()
        setContent {
            val amoled by viewModel.amoled.collectAsState()
            val palette by viewModel.palette.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()
            val appLanguage by viewModel.appLanguage.collectAsState()
            val matchLauncherIconToPalette by viewModel.matchLauncherIconToPalette.collectAsState()
            var appliedLanguage by remember { mutableStateOf(appLanguage) }
            LaunchedEffect(appLanguage) {
                if (appLanguage != appliedLanguage) {
                    appliedLanguage = appLanguage
                    if (Build.VERSION.SDK_INT >= 33) {
                        val tags = appLanguage.languageTag.orEmpty()
                        val manager = getSystemService(LocaleManager::class.java)
                        if (manager.applicationLocales.toLanguageTags() != tags) {
                            manager.applicationLocales = LocaleList.forLanguageTags(tags)
                        }
                    } else {
                        recreate()
                    }
                }
            }
            val systemLanguage = resources.configuration.locales[0]?.language.orEmpty()
            XyluneTheme(amoled = amoled, palette = palette, themeMode = themeMode) {
                CompositionLocalProvider(
                    LocalXyluneIconPalette provides if (matchLauncherIconToPalette) palette else ColorPalette.XYLUNE,
                    LocalXyluneUiLanguage provides resolvedUiLanguage(appLanguage, systemLanguage),
                ) {
                    val appName = stringResource(R.string.app_name)
                    XyluneApp(viewModel, this@MainActivity)
                    val container = (application as XyluneApplication).container
                    var crashReport by remember { mutableStateOf(container.crashReporter.read()) }
                    val renderSafeMode by viewModel.renderSafeMode.collectAsState()
                    crashReport?.let { report ->
                        val context = LocalContext.current
                        XyluneAlertDialog(
                            onDismissRequest = { container.crashReporter.clear(); crashReport = null },
                            title = { Text(uiText("$appName recovered a crash report")) },
                            text = {
                                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                    if (renderSafeMode) {
                                        Text(uiText("$appName reopened safely with generated widgets paused. Your chats and files were not deleted. You can dismiss this report and keep using the app, then retry full rendering when ready.\n"))
                                        OutlinedButton(onClick = { viewModel.setRenderSafeMode(false) }) { Text(uiText("Try full rendering again")) }
                                        Text(uiText("\n"))
                                    }
                                    Text(uiText("Copy this redacted diagnostic report if you need help diagnosing the failure. Review it before sharing.") + "\n\n" + report)
                                }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { container.crashReporter.clear(); crashReport = null }) { Text(uiText("Dismiss")) }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    context.getSystemService(ClipboardManager::class.java)
                                        .setPrimaryClip(ClipData.newPlainText("$appName crash report", report))
                                }) { Text(uiText("Copy report")) }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as XyluneApplication).container.openAiOAuth.onBrowserReturned()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            lifecycleScope.launch { viewModel.flushPersistentState() }
        }
        super.onStop()
    }

    private fun observeLauncherRestarts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.launcherRestartRequests.collect { desiredAlias ->
                    if (launcherRestartInFlight) return@collect
                    launcherRestartInFlight = true
                    viewModel.flushPersistentState()
                    val relaunch = PendingIntent.getActivity(
                        this@MainActivity,
                        20_020,
                        Intent(Intent.ACTION_MAIN).apply {
                            component = ComponentName(packageName, desiredAlias)
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    if (LauncherIconManager.requestStatefulRestart(
                            context = applicationContext,
                            desiredClassName = desiredAlias,
                            relaunchIntent = relaunch,
                        )
                    ) {
                        finishAffinity()
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    } else {
                        launcherRestartInFlight = false
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let {
                if (viewModel.handleCloudOAuthRedirect(it)) return
                viewModel.receivePortableArchive(it)
                return
            }
        }
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            else -> emptyList()
        }
        if (uris.size == 1 && isPortableArchiveIntent(intent, uris.single())) {
            viewModel.receivePortableArchive(uris.single())
            return
        }
        viewModel.receiveIntent(intent.getStringExtra(EXTRA_CONVERSATION_ID), uris)
    }

    private fun isPortableArchiveIntent(intent: Intent, uri: Uri): Boolean {
        if (intent.type == XYLUNE_CHAT_MIME || intent.type == XYLUNE_BACKUP_MIME) return true
        val name = uri.lastPathSegment?.lowercase().orEmpty()
        return name.endsWith(XYLUNE_CHAT_EXTENSION) || name.endsWith(XYLUNE_BACKUP_EXTENSION)
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T : Parcelable> Intent.parcelableExtra(key: String): T? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else getParcelableExtra(key)

    @Suppress("DEPRECATION")
    private inline fun <reified T : Parcelable> Intent.parcelableArrayListExtra(key: String): List<T> =
        if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(key, T::class.java).orEmpty() else getParcelableArrayListExtra<T>(key).orEmpty()

    companion object { const val EXTRA_CONVERSATION_ID = "conversation_id" }
}
