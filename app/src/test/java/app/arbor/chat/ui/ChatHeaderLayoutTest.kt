package app.arbor.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHeaderLayoutTest {
    @Test fun collapsedModelTagMovesCloserWithoutChangingExpandedBaseline() {
        val source = java.io.File("src/main/java/app/arbor/chat/ui/ChatCollapsingTranslucentTopBar.kt").readText()
        assertTrue(source.contains("(71.dp * (1f - travel)).toPx()"))
        assertTrue(source.contains(".offset(y = 37.dp)"))
        assertTrue(source.contains("expanded pill at the same 108 dp baseline"))
    }
}
