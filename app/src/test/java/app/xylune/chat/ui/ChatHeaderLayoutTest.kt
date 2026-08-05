package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHeaderLayoutTest {
    @Test fun collapsedModelTagMovesCloserWithoutChangingExpandedBaseline() {
        val source = java.io.File("src/main/java/app/xylune/chat/ui/ChatCollapsingTranslucentTopBar.kt").readText()
        assertTrue(source.contains("(71.dp * (1f - travel)).toPx()"))
        assertTrue(source.contains(".offset(y = 37.dp)"))
        assertTrue(source.contains("expanded pill at the same 108 dp baseline"))
        assertTrue(source.contains("val titleEndPadding = 72.dp + (48.dp * travel)"))
        assertTrue(source.contains(".padding(start = 72.dp, end = titleEndPadding)"))
        assertTrue(source.contains("modifier = Modifier.zIndex(4f)"))
        assertTrue(source.contains(".zIndex(5f)"))
    }
}
