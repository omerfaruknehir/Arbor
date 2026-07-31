from pathlib import Path

root = Path.cwd()


def edit(rel: str, old: str, new: str, count: int = 1) -> None:
    path = root / rel
    source = path.read_text()
    if source.count(old) < count:
        raise SystemExit(f"Missing expected source in {rel}: {old[:100]!r}")
    path.write_text(source.replace(old, new, count))


# Reuse exact palette-specific launcher art in drawer, setup and licenses.
path = root / "app/src/main/java/app/arbor/chat/ui/PaletteVisuals.kt"
source = path.read_text()
for line in (
    "import androidx.compose.foundation.Canvas\n",
    "import androidx.compose.ui.geometry.CornerRadius\n",
    "import androidx.compose.ui.geometry.Offset\n",
    "import androidx.compose.ui.geometry.Size\n",
    "import androidx.compose.ui.graphics.Brush\n",
    "import androidx.compose.ui.graphics.Path\n",
    "import androidx.compose.ui.graphics.StrokeCap\n",
    "import androidx.compose.ui.graphics.drawscope.Stroke\n",
    "import androidx.compose.ui.semantics.contentDescription\n",
    "import androidx.compose.ui.semantics.semantics\n",
    "import kotlin.math.min\n",
):
    source = source.replace(line, "")
source = source.replace(
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.staticCompositionLocalOf\n",
    1,
)
start = source.index("/** Arbor's in-app mark")
end = source.index("@Composable\ninternal fun LauncherIconPreview", start)
source = source[:start] + '''/** Palette used by every in-app Arbor icon. It mirrors the launcher alias exactly. */
internal val LocalArborIconPalette = staticCompositionLocalOf { ColorPalette.ARBOR }

/** Exact drawable-backed copy of the currently selected launcher icon artwork. */
@Composable
internal fun ArborMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val palette = LocalArborIconPalette.current
    Image(
        painter = painterResource(palette.launcherPreviewDrawable),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

''' + source[end:]
source = source.replace(
    "private val ColorPalette.launcherPreviewDrawable: Int",
    "internal val ColorPalette.launcherPreviewDrawable: Int",
    1,
)
path.write_text(source)

edit(
    "app/src/main/java/app/arbor/chat/MainActivity.kt",
    "import androidx.compose.runtime.collectAsState\n",
    "import androidx.compose.runtime.collectAsState\nimport androidx.compose.runtime.CompositionLocalProvider\n",
)
edit(
    "app/src/main/java/app/arbor/chat/MainActivity.kt",
    "import app.arbor.chat.ui.ArborApp\n",
    "import app.arbor.chat.ui.ArborApp\nimport app.arbor.chat.ui.LocalArborIconPalette\n",
)
edit(
    "app/src/main/java/app/arbor/chat/MainActivity.kt",
    "import app.arbor.chat.ui.theme.ArborTheme\n",
    "import app.arbor.chat.ui.theme.ArborTheme\nimport app.arbor.chat.settings.ColorPalette\n",
)
edit(
    "app/src/main/java/app/arbor/chat/MainActivity.kt",
    "            val themeMode by viewModel.themeMode.collectAsState()\n            ArborTheme",
    "            val themeMode by viewModel.themeMode.collectAsState()\n            val matchLauncherIconToPalette by viewModel.matchLauncherIconToPalette.collectAsState()\n            ArborTheme",
)
edit(
    "app/src/main/java/app/arbor/chat/MainActivity.kt",
    "            ArborTheme(amoled = amoled, palette = palette, themeMode = themeMode) {\n                val appName",
    "            ArborTheme(amoled = amoled, palette = palette, themeMode = themeMode) {\n                CompositionLocalProvider(\n                    LocalArborIconPalette provides if (matchLauncherIconToPalette) palette else ColorPalette.ARBOR,\n                ) {\n                    val appName",
)
edit(
    "app/src/main/java/app/arbor/chat/MainActivity.kt",
    "                }\n            }\n        }\n    }\n\n    override fun onResume()",
    "                }\n                }\n            }\n        }\n    }\n\n    override fun onResume()",
)
path = root / "app/src/main/java/app/arbor/chat/MainActivity.kt"
source = path.read_text()
body_start = source.index("                ArborApp(viewModel, this@MainActivity)")
body_end = source.index("                }\n                }\n            }", body_start) + len("                }\n")
body = source[body_start:body_end]
source = source[:body_start] + "\n".join("    " + line for line in body.rstrip("\n").splitlines()) + "\n" + source[body_end:]
path.write_text(source)

path = root / "app/src/main/java/app/arbor/chat/ui/LicenseCatalogScreen.kt"
source = path.read_text()
function_start = source.index("@Composable\nprivate fun LicenseIcon(")
block_start = source.index("    Surface(", function_start)
block_end = source.index("\n}\n\n@Composable\nprivate fun LicenseIconFallback", block_start)
new_block = '''    Surface(
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (component.id == "arbor") {
            ArborMark(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentDescription = "Arbor",
            )
        } else {
            when {
                svgRequest != null -> SubcomposeAsyncImage(
                    model = svgRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(9.dp),
                    contentScale = ContentScale.Fit,
                    loading = {},
                    error = { LicenseIconFallback(component.name) },
                )
                rasterImage != null -> Image(
                    bitmap = rasterImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(9.dp),
                    contentScale = ContentScale.Fit,
                )
                else -> LicenseIconFallback(component.name)
            }
        }
    }'''
path.write_text(source[:block_start] + new_block + source[block_end:])

# Move launcher component mutation into a lightweight secondary process.
edit(
    "app/src/main/java/app/arbor/chat/settings/LauncherIconManager.kt",
    "import android.content.Context\n",
    "import android.content.Context\nimport android.content.Intent\n",
)
edit(
    "app/src/main/java/app/arbor/chat/settings/LauncherIconManager.kt",
    '''/**
 * Switches between manifest-declared launcher aliases without restarting Arbor.
 *
 * Launcher aliases target a zero-UI trampoline instead of MainActivity, so the
 * foreground task is never rooted at a component being disabled. Android 13+
 * receives one atomic component-state transaction; older versions retain the
 * same enable-first ordering and DONT_KILL_APP behavior.
 */''',
    '''/**
 * Requests launcher alias changes through a dedicated process.
 *
 * Some OEM package managers ignore DONT_KILL_APP when aliases are toggled by the
 * foreground process. The explicit receiver performs the package mutation in
 * :launcher_icon, keeping MainActivity and its task out of the process which is
 * touching component state.
 */''',
)
edit(
    "app/src/main/java/app/arbor/chat/settings/LauncherIconManager.kt",
    '    private const val TAG = "ArborLauncherIcon"\n',
    '    private const val TAG = "ArborLauncherIcon"\n    internal const val EXTRA_DESIRED_ALIAS = "app.arbor.chat.extra.DESIRED_LAUNCHER_ALIAS"\n',
)
path = root / "app/src/main/java/app/arbor/chat/settings/LauncherIconManager.kt"
source = path.read_text()
start = source.index("    fun apply(context: Context")
end = source.index("    @RequiresApi", start)
source = source[:start] + '''    fun apply(context: Context, matchPalette: Boolean, palette: ColorPalette): Boolean {
        val appContext = context.applicationContext
        val desiredClassName = aliasClassName(matchPalette, palette)
        return runCatching {
            appContext.sendBroadcast(
                Intent(appContext, LauncherIconSwitchReceiver::class.java)
                    .setPackage(appContext.packageName)
                    .putExtra(EXTRA_DESIRED_ALIAS, desiredClassName),
            )
            true
        }.onFailure { error ->
            Log.w(TAG, "Could not request launcher icon alias update", error)
        }.getOrDefault(false)
    }

    internal fun applyDirect(context: Context, desiredClassName: String): Boolean {
        if (desiredClassName !in allAliases) return false
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applyAtomically(packageManager, appContext.packageName, desiredClassName)
            } else {
                applyEnableFirst(packageManager, appContext.packageName, desiredClassName)
            }
            true
        }.onFailure { error ->
            Log.w(TAG, "Could not update launcher icon alias", error)
        }.getOrDefault(false)
    }

''' + source[end:]
path.write_text(source)

(root / "app/src/main/java/app/arbor/chat/settings/LauncherIconSwitchReceiver.kt").write_text('''package app.arbor.chat.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Performs launcher component mutations outside Arbor's foreground process. */
class LauncherIconSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val desiredAlias = intent.getStringExtra(LauncherIconManager.EXTRA_DESIRED_ALIAS) ?: return
        LauncherIconManager.applyDirect(context, desiredAlias)
    }
}
''')

edit(
    "app/src/main/AndroidManifest.xml",
    '''            android:exported="false"
            android:noHistory="true"
            android:theme="@style/Theme.Arbor.Launcher" />''',
    '''            android:exported="false"
            android:noHistory="true"
            android:process=":launcher_icon"
            android:theme="@style/Theme.Arbor.Launcher" />

        <receiver
            android:name=".settings.LauncherIconSwitchReceiver"
            android:exported="false"
            android:process=":launcher_icon" />''',
)
edit(
    "app/src/main/java/app/arbor/chat/ArborApplication.kt",
    "import android.app.Application\n",
    "import android.app.ActivityManager\nimport android.app.Application\nimport android.content.Context\nimport android.os.Build\nimport android.os.Process\n",
)
edit(
    "app/src/main/java/app/arbor/chat/ArborApplication.kt",
    "        super.onCreate()\n        val crashReporter",
    "        super.onCreate()\n        if (isLauncherIconProcess()) return\n        val crashReporter",
)
edit(
    "app/src/main/java/app/arbor/chat/ArborApplication.kt",
    '''    }
}

class AppContainer''',
    '''    }

    private fun isLauncherIconProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val pid = Process.myPid()
            getSystemService(Context.ACTIVITY_SERVICE)
                .let { it as? ActivityManager }
                ?.runningAppProcesses
                ?.firstOrNull { it.pid == pid }
                ?.processName
                .orEmpty()
        }
        return processName.endsWith(":launcher_icon")
    }
}

class AppContainer''',
)
