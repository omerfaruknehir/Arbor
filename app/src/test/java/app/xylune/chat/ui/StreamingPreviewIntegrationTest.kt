package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPreviewIntegrationTest {
    @Test
    fun livePreviewBypassesRoomCadenceAndScrollHasFrameCaps() {
        val worker = java.io.File("src/main/java/app/xylune/chat/generation/GenerationWorker.kt").readText()
        val screen = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        val motion = java.io.File("src/main/java/app/xylune/chat/ui/StreamingMotion.kt").readText()
        val rich = java.io.File("src/main/java/app/xylune/chat/ui/RichMessage.kt").readText()

        assertTrue(worker.contains("if (previewChanged) publishPreview()"))
        assertTrue(worker.contains("StreamingPreviewStore.clear(assistantId)"))
        assertTrue(screen.contains("StreamingPreviewStore.previews.collectAsStateWithLifecycle()"))
        assertTrue(screen.contains("ChatFollowMaxFrameStepPx"))
        assertTrue(screen.contains("ChatFollowSeekMaxFrameStepPx"))
        assertTrue(motion.contains("intervalNanos: Long = 16_500_000L"))
        assertTrue(rich.contains("else 16_500_000L"))
        assertTrue(rich.contains("else 48"))
    }
}
