package app.xylune.chat

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XyluneSmokeTest {
    @Test
    fun applicationStartsAndEncryptedDatabaseOpens() {
        val application = ApplicationProvider.getApplicationContext<XyluneApplication>()
        assertNotNull(application.container.database.openHelper.writableDatabase)
    }
}
