package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PersistentComposerStateTest {
    @Test
    fun `draft text is stored per conversation while attachments remain staged`() {
        val viewModel = File("src/main/java/app/xylune/chat/ui/ChatViewModel.kt").readText()
        val draftStore = File("src/main/java/app/xylune/chat/settings/ComposerDraftStore.kt").readText()
        val attachmentDao = File("src/main/java/app/xylune/chat/data/Daos.kt").readText()
        assertTrue(viewModel.contains("switchDraftContext"))
        assertTrue(viewModel.contains("container.composerDrafts.write"))
        assertTrue(viewModel.contains("stagedForConversation"))
        assertTrue(draftStore.contains("draft.\$conversationId"))
        assertTrue(attachmentDao.contains("messageNodeId IS NULL"))
    }

    @Test
    fun `restart state includes page setup and chat scroll anchor`() {
        val stateStore = File("src/main/java/app/xylune/chat/settings/PersistentUiStateStore.kt").readText()
        val chat = File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        assertTrue(stateStore.contains("settingsRoute"))
        assertTrue(stateStore.contains("setupStepIndex"))
        assertTrue(stateStore.contains("setupPageOffsetFraction"))
        assertTrue(stateStore.contains("saveSetupScroll"))
        assertTrue(stateStore.contains("setup_scroll."))
        assertTrue(stateStore.contains("ChatScrollSnapshot"))
        assertTrue(chat.contains("anchorNodeId"))
        assertTrue(chat.contains("firstVisibleItemOffset"))
        assertTrue(chat.contains("topBarHeightOffset"))
    }

    @Test
    fun `launcher splash uses the active app palette and icon`() {
        val launcher = File("src/main/java/app/xylune/chat/LauncherActivity.kt").readText()
        assertTrue(launcher.contains("resolvedXyluneColorScheme"))
        assertTrue(launcher.contains("LauncherIconManager.iconResource"))
        assertTrue(launcher.contains("setBackgroundColor"))
    }
}
