package app.xylune.chat.ui

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineLicenseIconAssetTest {
    @Test
    fun everyEmbeddedCatalogIconCanBeDecoded() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val catalog = assets.open("licenses/catalog.json")
            .bufferedReader()
            .use { JSONObject(it.readText()) }
        val components = catalog.getJSONArray("components")

        for (index in 0 until components.length()) {
            val component = components.getJSONObject(index)
            val name = component.getString("name")
            val icon = component.getString("icon")
            val assetPath = "licenses/$icon"

            if (icon.endsWith(".svg", ignoreCase = true)) {
                val svg = assets.open(assetPath).bufferedReader().use { it.readText() }
                assertTrue("$name has an invalid SVG asset", svg.contains("<svg"))
            } else {
                val bitmap = assets.open(assetPath).use(BitmapFactory::decodeStream)
                assertNotNull("$name has an undecodable raster icon", bitmap)
                bitmap?.recycle()
            }
        }
    }
}
