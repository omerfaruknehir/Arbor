package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MessageUsageMenuRegressionTest {
    private fun repositoryFile(path: String): File = sequenceOf(File(path), File("..", path))
        .firstOrNull(File::isFile)
        ?: error("Could not locate repository file: $path")

    @Test
    fun `message footer exposes usage and share overflow menu`() {
        val chat = repositoryFile("app/src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        val usageUi = repositoryFile("app/src/main/java/app/xylune/chat/ui/UsageDetailsUi.kt").readText()

        assertTrue(chat.contains("MessageContextMenu(message)"))
        assertTrue(usageUi.contains("Text(\"Usage details\")"))
        assertTrue(usageUi.contains("Text(\"Share message\")"))
        assertTrue(usageUi.contains("attachmentDao().forMessage(message.nodeId)"))
        assertTrue(usageUi.contains("Intent.ACTION_SEND_MULTIPLE"))
        assertTrue(usageUi.contains("Non-cached input"))
        assertTrue(usageUi.contains("Provider calls"))
    }
}
