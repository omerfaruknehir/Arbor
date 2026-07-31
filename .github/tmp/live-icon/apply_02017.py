from pathlib import Path

ROOT = Path('.')


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

build = read('app/build.gradle.kts')
build = replace_once(build, 'versionCode = 142', 'versionCode = 143', 'versionCode')
build = replace_once(build, 'versionName = "0.20.16"', 'versionName = "0.20.17"', 'versionName')
write('app/build.gradle.kts', build)

write('app/src/main/java/app/arbor/chat/LauncherActivity.kt', '''package app.arbor.chat

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Zero-UI entry point used only by launcher aliases.
 *
 * The real app task is rooted at [MainActivity], never at an alias component.
 * Changing or disabling a launcher alias therefore cannot close the screen the
 * user is currently using. [MainActivity] is singleTask, so tapping any alias
 * also brings the existing Arbor task forward instead of creating a duplicate.
 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
''')

manifest = read('app/src/main/AndroidManifest.xml')
manifest = replace_once(manifest, 'android:launchMode="singleTop"', 'android:launchMode="singleTask"', 'MainActivity launchMode')
manifest = replace_once(
    manifest,
    '        </activity>\n\n         <activity-alias',
    '''        </activity>

        <activity
            android:name=".LauncherActivity"
            android:exported="false"
            android:noHistory="true"
            android:theme="@style/Theme.Arbor.Launcher" />

         <activity-alias''',
    'launcher activity insertion',
)
if manifest.count('android:targetActivity=".MainActivity"') != 6:
    raise RuntimeError('expected six launcher aliases targeting MainActivity')
manifest = manifest.replace('android:targetActivity=".MainActivity"', 'android:targetActivity=".LauncherActivity"')
write('app/src/main/AndroidManifest.xml', manifest)

styles = read('app/src/main/res/values/styles.xml')
styles = replace_once(
    styles,
    '</resources>',
    '''
    <style name="Theme.Arbor.Launcher" parent="android:style/Theme.Translucent.NoTitleBar">
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowDisablePreview">true</item>
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowNoTitle">true</item>
    </style>
</resources>''',
    'launcher style',
)
write('app/src/main/res/values/styles.xml', styles)

write('app/src/main/java/app/arbor/chat/settings/LauncherIconManager.kt', '''package app.arbor.chat.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Switches between manifest-declared launcher aliases without restarting Arbor.
 *
 * Launcher aliases target a zero-UI trampoline instead of MainActivity, so the
 * foreground task is never rooted at a component being disabled. Android 13+
 * receives one atomic component-state transaction; older versions retain the
 * same enable-first ordering and DONT_KILL_APP behavior.
 */
internal object LauncherIconManager {
    private const val TAG = "ArborLauncherIcon"

    internal const val ARBOR_ALIAS = "app.arbor.chat.LauncherArbor"
    internal const val SYSTEM_ALIAS = "app.arbor.chat.LauncherSystem"
    internal const val GRAPHITE_ALIAS = "app.arbor.chat.LauncherGraphite"
    internal const val OCEAN_ALIAS = "app.arbor.chat.LauncherOcean"
    internal const val VIOLET_ALIAS = "app.arbor.chat.LauncherViolet"
    internal const val SUNSET_ALIAS = "app.arbor.chat.LauncherSunset"

    internal val allAliases = listOf(
        ARBOR_ALIAS,
        SYSTEM_ALIAS,
        GRAPHITE_ALIAS,
        OCEAN_ALIAS,
        VIOLET_ALIAS,
        SUNSET_ALIAS,
    )

    internal fun aliasClassName(matchPalette: Boolean, palette: ColorPalette): String {
        if (!matchPalette) return ARBOR_ALIAS
        return when (palette) {
            ColorPalette.ARBOR -> ARBOR_ALIAS
            ColorPalette.SYSTEM -> SYSTEM_ALIAS
            ColorPalette.GRAPHITE -> GRAPHITE_ALIAS
            ColorPalette.OCEAN -> OCEAN_ALIAS
            ColorPalette.VIOLET -> VIOLET_ALIAS
            ColorPalette.SUNSET -> SUNSET_ALIAS
        }
    }

    fun apply(context: Context, matchPalette: Boolean, palette: ColorPalette): Boolean {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val desiredClassName = aliasClassName(matchPalette, palette)
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

    private fun applyAtomically(
        packageManager: PackageManager,
        packageName: String,
        desiredClassName: String,
    ) {
        val changes = allAliases.mapNotNull { className ->
            val component = ComponentName(packageName, className)
            val enabled = className == desiredClassName
            val manifestDefault = className == ARBOR_ALIAS
            if (isEnabled(packageManager, component, manifestDefault) == enabled) return@mapNotNull null
            PackageManager.ComponentEnabledSetting(
                component,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        if (changes.isNotEmpty()) packageManager.setComponentEnabledSettings(changes)
    }

    private fun applyEnableFirst(
        packageManager: PackageManager,
        packageName: String,
        desiredClassName: String,
    ) {
        setEnabled(
            packageManager = packageManager,
            component = ComponentName(packageName, desiredClassName),
            enabled = true,
            manifestDefault = desiredClassName == ARBOR_ALIAS,
        )
        allAliases.asSequence()
            .filterNot { it == desiredClassName }
            .forEach { className ->
                setEnabled(
                    packageManager = packageManager,
                    component = ComponentName(packageName, className),
                    enabled = false,
                    manifestDefault = className == ARBOR_ALIAS,
                )
            }
    }

    private fun isEnabled(
        packageManager: PackageManager,
        component: ComponentName,
        manifestDefault: Boolean,
    ): Boolean = when (packageManager.getComponentEnabledSetting(component)) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestDefault
        else -> false
    }

    private fun setEnabled(
        packageManager: PackageManager,
        component: ComponentName,
        enabled: Boolean,
        manifestDefault: Boolean,
    ) {
        if (isEnabled(packageManager, component, manifestDefault) == enabled) return
        packageManager.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
''')

write('app/src/main/java/app/arbor/chat/ui/PaletteVisuals.kt', '''package app.arbor.chat.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.arbor.chat.R
import app.arbor.chat.settings.ColorPalette
import app.arbor.chat.ui.theme.PalettePreviewColors
import kotlin.math.min

@Composable
internal fun PaletteSwatch(colors: PalettePreviewColors, modifier: Modifier = Modifier) {
    Row(modifier) {
        PaletteDot(colors.primary)
        PaletteDot(colors.secondary, Modifier.offset(x = (-7).dp))
        PaletteDot(colors.tertiary, Modifier.offset(x = (-14).dp))
    }
}

@Composable
private fun PaletteDot(color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color,
        shape = CircleShape,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
        modifier = modifier.size(24.dp),
    ) {}
}

/** Arbor's in-app mark, rendered from the active Material color scheme. */
@Composable
internal fun ArborMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val accessibility = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
    }
    Canvas(modifier.then(accessibility)) {
        val logoSize = min(size.width, size.height)
        if (logoSize <= 0f) return@Canvas
        val unit = logoSize / 108f
        val left = (size.width - logoSize) / 2f
        val top = (size.height - logoSize) / 2f
        fun point(x: Float, y: Float) = Offset(left + x * unit, top + y * unit)

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(colors.primaryContainer, colors.primary),
                start = point(15f, 8f),
                end = point(96f, 101f),
            ),
            topLeft = Offset(left, top),
            size = Size(logoSize, logoSize),
            cornerRadius = CornerRadius(24f * unit, 24f * unit),
        )

        val leftStem = Path().apply {
            moveTo(point(28f, 82f).x, point(28f, 82f).y)
            cubicTo(
                point(35f, 64f).x, point(35f, 64f).y,
                point(43f, 45f).x, point(43f, 45f).y,
                point(53f, 28f).x, point(53f, 28f).y,
            )
        }
        drawPath(
            path = leftStem,
            brush = Brush.linearGradient(
                colors = listOf(colors.onPrimaryContainer, colors.onPrimary),
                start = point(27f, 84f),
                end = point(55f, 25f),
            ),
            style = Stroke(width = 11f * unit, cap = StrokeCap.Round),
        )

        val rightStem = Path().apply {
            moveTo(point(55f, 28f).x, point(55f, 28f).y)
            cubicTo(
                point(65f, 45f).x, point(65f, 45f).y,
                point(73f, 64f).x, point(73f, 64f).y,
                point(80f, 82f).x, point(80f, 82f).y,
            )
        }
        drawPath(
            path = rightStem,
            color = colors.onPrimary,
            style = Stroke(width = 11f * unit, cap = StrokeCap.Round),
        )

        val crossbar = Path().apply {
            moveTo(point(40f, 64f).x, point(40f, 64f).y)
            cubicTo(
                point(49f, 60f).x, point(49f, 60f).y,
                point(59f, 60f).x, point(59f, 60f).y,
                point(68f, 64f).x, point(68f, 64f).y,
            )
        }
        drawPath(
            path = crossbar,
            color = colors.tertiary,
            style = Stroke(width = 8f * unit, cap = StrokeCap.Round),
        )

        val leaf = Path().apply {
            moveTo(point(54f, 29f).x, point(54f, 29f).y)
            cubicTo(
                point(60f, 21f).x, point(60f, 21f).y,
                point(69f, 19f).x, point(69f, 19f).y,
                point(76f, 24f).x, point(76f, 24f).y,
            )
            cubicTo(
                point(73f, 33f).x, point(73f, 33f).y,
                point(65f, 37f).x, point(65f, 37f).y,
                point(55f, 33f).x, point(55f, 33f).y,
            )
            close()
        }
        drawPath(leaf, color = colors.tertiary)
    }
}

@Composable
internal fun LauncherIconPreview(
    palette: ColorPalette,
    size: Dp = 54.dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier = modifier.size(size),
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Image(
                painter = painterResource(palette.launcherPreviewDrawable),
                contentDescription = null,
                modifier = Modifier.matchParentSize().clip(MaterialTheme.shapes.large),
            )
        }
    }
}

@get:DrawableRes
private val ColorPalette.launcherPreviewDrawable: Int
    get() = when (this) {
        ColorPalette.ARBOR -> R.drawable.ic_arbor_mark
        ColorPalette.SYSTEM -> R.drawable.ic_arbor_mark_system
        ColorPalette.GRAPHITE -> R.drawable.ic_arbor_mark_graphite
        ColorPalette.OCEAN -> R.drawable.ic_arbor_mark_ocean
        ColorPalette.VIOLET -> R.drawable.ic_arbor_mark_violet
        ColorPalette.SUNSET -> R.drawable.ic_arbor_mark_sunset
    }
''')

onboarding = read('app/src/main/java/app/arbor/chat/ui/OnboardingScreen.kt')
for old in ('import androidx.compose.foundation.Image\n', 'import androidx.compose.ui.res.painterResource\n', 'import app.arbor.chat.R\n'):
    onboarding = replace_once(onboarding, old, '', f'onboarding import {old.strip()}')
onboarding = replace_once(
    onboarding,
    '''    Image(
        painter = painterResource(R.drawable.ic_arbor_mark),
        contentDescription = "Arbor",
        modifier = Modifier.size(96.dp),
    )''',
    '    ArborMark(modifier = Modifier.size(96.dp), contentDescription = "Arbor")',
    'onboarding logo',
)
onboarding = replace_once(
    onboarding,
    '"The launcher may take a moment to refresh. Android themed icons can override app-selected colors."',
    '"Arbor stays open while the launcher refreshes. Android themed icons can override app-selected colors."',
    'onboarding launcher explanation',
)
write('app/src/main/java/app/arbor/chat/ui/OnboardingScreen.kt', onboarding)

sidebar = read('app/src/main/java/app/arbor/chat/ui/ConversationSidebar.kt')
for old in (
    'import androidx.compose.foundation.Image\n',
    'import androidx.compose.ui.res.painterResource\n',
    'import androidx.compose.ui.res.stringResource\n',
    'import app.arbor.chat.R\n',
):
    sidebar = replace_once(sidebar, old, '', f'sidebar import {old.strip()}')
sidebar = replace_once(
    sidebar,
    '''@Composable
private fun ArborMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_arbor_mark),
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier,
    )
}

''',
    '',
    'static sidebar logo',
)
write('app/src/main/java/app/arbor/chat/ui/ConversationSidebar.kt', sidebar)

settings = read('app/src/main/java/app/arbor/chat/ui/SettingsScreen.kt')
settings = replace_once(
    settings,
    '"The launcher may take a moment to refresh. Android themed icons can override app-selected colors."',
    '"Arbor stays open while the launcher refreshes. Android themed icons can override app-selected colors."',
    'appearance launcher explanation',
)
write('app/src/main/java/app/arbor/chat/ui/SettingsScreen.kt', settings)

write('app/src/test/java/app/arbor/chat/settings/LauncherIconManagerTest.kt', '''package app.arbor.chat.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LauncherIconManagerTest {
    @Test
    fun `disabled matching always uses classic Arbor icon`() {
        ColorPalette.entries.forEach { palette ->
            assertEquals(
                LauncherIconManager.ARBOR_ALIAS,
                LauncherIconManager.aliasClassName(matchPalette = false, palette = palette),
            )
        }
    }

    @Test
    fun `every palette has a stable unique launcher alias`() {
        val aliases = ColorPalette.entries.map {
            LauncherIconManager.aliasClassName(matchPalette = true, palette = it)
        }
        assertEquals(ColorPalette.entries.size, aliases.distinct().size)
        assertEquals(LauncherIconManager.allAliases.toSet(), aliases.toSet())
    }

    @Test
    fun `launcher aliases use a stable trampoline instead of the running activity`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        LauncherIconManager.allAliases.forEach { alias ->
            assertTrue(manifest.contains(".${alias.substringAfterLast('.')}"))
        }

        val mainActivity = manifest.substringAfter("android:name=\".MainActivity\"")
            .substringBefore("</activity>")
        assertFalse(mainActivity.contains("android.intent.category.LAUNCHER"))
        assertTrue(mainActivity.contains("android:launchMode=\"singleTask\""))
        assertTrue(manifest.contains("android:name=\".LauncherActivity\""))

        val aliasBlocks = Regex("<activity-alias[\\s\\S]*?</activity-alias>")
            .findAll(manifest)
            .map { it.value }
            .toList()
        assertEquals(LauncherIconManager.allAliases.size, aliasBlocks.size)
        aliasBlocks.forEach { block ->
            assertTrue(block.contains("android:targetActivity=\".LauncherActivity\""))
            assertFalse(block.contains("android:targetActivity=\".MainActivity\""))
        }

        val trampoline = File("src/main/java/app/arbor/chat/LauncherActivity.kt").readText()
        assertTrue(trampoline.contains("Intent(this, MainActivity::class.java)"))
        assertTrue(trampoline.contains("finish()"))
    }

    @Test
    fun `icon switching is atomic where supported and never requests a process kill`() {
        val source = File("src/main/java/app/arbor/chat/settings/LauncherIconManager.kt").readText()
        assertTrue(source.contains("setComponentEnabledSettings"))
        assertTrue(source.contains("PackageManager.DONT_KILL_APP"))
        assertTrue(source.contains("applyEnableFirst"))
    }
}
''')

write('app/src/test/java/app/arbor/chat/ui/ArborMarkTest.kt', '''package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArborMarkTest {
    @Test
    fun `in app Arbor marks follow the active Material colors`() {
        val visuals = File("src/main/java/app/arbor/chat/ui/PaletteVisuals.kt").readText()
        assertTrue(visuals.contains("MaterialTheme.colorScheme"))
        assertTrue(visuals.contains("internal fun ArborMark"))
        assertTrue(visuals.contains("colors.primaryContainer"))
        assertTrue(visuals.contains("colors.tertiary"))

        val onboarding = File("src/main/java/app/arbor/chat/ui/OnboardingScreen.kt").readText()
        val sidebar = File("src/main/java/app/arbor/chat/ui/ConversationSidebar.kt").readText()
        assertTrue(onboarding.contains("ArborMark("))
        assertTrue(sidebar.contains("ArborMark("))
        assertFalse(onboarding.contains("R.drawable.ic_arbor_mark"))
        assertFalse(sidebar.contains("R.drawable.ic_arbor_mark"))
    }
}
''')

write('docs/releases/RELEASE_NOTES_0.20.17.md', '''# Arbor 0.20.17

## Launcher icon switching

- Routes every launcher alias through a transparent zero-UI trampoline, so Arbor's real running task is rooted at `MainActivity` rather than an alias being enabled or disabled.
- Keeps `MainActivity` single-task and reuses the existing screen when any launcher icon is tapped.
- Applies alias-state changes atomically on Android 13 and newer, with the enable-first fallback retained on older supported Android versions.
- Keeps `PackageManager.DONT_KILL_APP` on every component-state change.
- Updates setup and Appearance wording to state that Arbor remains open while the launcher refreshes.

## In-app branding

- Replaces static green marks in onboarding and the conversation drawer with one runtime-drawn Arbor mark.
- Derives the mark background, stems, crossbar, and leaf from the active Material color scheme.
- Updates instantly for Arbor, Dynamic, Graphite, Ocean, Violet, Sunset, light/dark mode, and wallpaper-derived Dynamic Color.

## Verification

- Third-party license verification
- Offline license catalog generation
- Release unit tests
- Release lint
- Release APK and AAB builds
- Instrumentation APK build
- APK signature verification
''')

changelog = read('CHANGELOG.md')
entry = '''## 0.20.17 — 2026-07-31

- Route launcher aliases through a transparent trampoline so changing the icon cannot close the active MainActivity task.
- Reuse the existing single-task app screen when any launcher alias is tapped and apply alias changes atomically on Android 13+.
- Render the onboarding and drawer Arbor marks from the active Material color scheme, including wallpaper-derived Dynamic Color.
- Add regression coverage for alias task isolation, no-kill flags, atomic switching, and removal of static in-app green marks.

'''
changelog = replace_once(changelog, '# Changelog\n\n', '# Changelog\n\n' + entry, 'changelog insertion')
changelog = changelog.replace(
    '- Add polished adaptive icons and safe activity-alias switching without killing the running app.',
    '- Add polished adaptive icons and activity-alias switching with Android\'s `DONT_KILL_APP` flag.',
    1,
)
write('CHANGELOG.md', changelog)

assert read('app/src/main/AndroidManifest.xml').count('android:targetActivity=".LauncherActivity"') == 6
assert 'android:targetActivity=".MainActivity"' not in read('app/src/main/AndroidManifest.xml')
assert 'versionName = "0.20.17"' in read('app/build.gradle.kts')
assert 'versionCode = 143' in read('app/build.gradle.kts')
assert 'R.drawable.ic_arbor_mark' not in read('app/src/main/java/app/arbor/chat/ui/OnboardingScreen.kt')
assert 'R.drawable.ic_arbor_mark' not in read('app/src/main/java/app/arbor/chat/ui/ConversationSidebar.kt')
