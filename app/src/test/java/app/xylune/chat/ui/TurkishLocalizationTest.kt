package app.xylune.chat.ui

import app.xylune.chat.settings.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TurkishLocalizationTest {
    private fun repositoryFile(path: String): File = sequenceOf(File(path), File("..", path))
        .firstOrNull(File::isFile) ?: error("Could not locate repository file: $path")

    @Test
    fun `language resolver supports system english and turkish`() {
        assertEquals(XyluneUiLanguage.TURKISH, resolvedUiLanguage(AppLanguage.TURKISH, "en"))
        assertEquals(XyluneUiLanguage.ENGLISH, resolvedUiLanguage(AppLanguage.ENGLISH, "tr"))
        assertEquals(XyluneUiLanguage.TURKISH, resolvedUiLanguage(AppLanguage.SYSTEM, "tr"))
        assertEquals(XyluneUiLanguage.ENGLISH, resolvedUiLanguage(AppLanguage.SYSTEM, "de"))
    }

    @Test
    fun `core app chrome has Turkish translations`() {
        assertEquals("Ayarlar", localizeUiText("Settings", XyluneUiLanguage.TURKISH))
        assertEquals("Yeni sohbet", localizeUiText("New chat", XyluneUiLanguage.TURKISH))
        assertEquals("Sağlayıcılar ve modeller", localizeUiText("Providers & models", XyluneUiLanguage.TURKISH))
        assertEquals("Görseller", localizeUiText("Images", XyluneUiLanguage.TURKISH))
        assertEquals("Sayfa 2 / 7", localizeUiText("Page 2 of 7", XyluneUiLanguage.TURKISH))
        assertEquals("Kaynak 3", localizeUiText("Source 3", XyluneUiLanguage.TURKISH))
        assertEquals("3 listelenen ağ kaynağına izin ver", localizeUiText("Allow 3 listed network origins?", XyluneUiLanguage.TURKISH))
        assertEquals("2 devre dışı bellek silinsin mi?", localizeUiText("Delete 2 disabled memories?", XyluneUiLanguage.TURKISH))
        assertEquals("Deneme 4 · 2 hata", localizeUiText("Attempt 4 · 2 error(s)", XyluneUiLanguage.TURKISH))
    }

    @Test
    fun `android advertises Turkish and portable settings keep language`() {
        val locales = repositoryFile("app/src/main/res/xml/locales_config.xml").readText()
        val transfer = repositoryFile("app/src/main/java/app/xylune/chat/transfer/AppSettingsArchiveStore.kt").readText()
        val settings = repositoryFile("app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        assertTrue(locales.contains("android:name=\"tr\""))
        assertTrue(transfer.contains("val appLanguage: String"))
        assertTrue(transfer.contains("preferences.setAppLanguage"))
        assertTrue(settings.contains("SettingsRoute.LANGUAGE"))
        assertTrue(settings.contains("AppLanguage.TURKISH"))
    }
}
