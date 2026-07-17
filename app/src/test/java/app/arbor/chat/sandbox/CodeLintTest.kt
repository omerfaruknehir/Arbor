package app.arbor.chat.sandbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeLintTest {
    @Test fun invalidJsonIsAnError() {
        assertTrue(StaticCodeLinter.lint("json", "{\"broken\": }").hasErrors)
    }

    @Test fun unmatchedDelimiterIsAnError() {
        assertTrue(StaticCodeLinter.lint("kotlin", "fun value() = listOf(1, 2").hasErrors)
    }

    @Test fun comparisonsDoNotLookLikeUnclosedGenericBrackets() {
        assertFalse(StaticCodeLinter.lint("cpp", "if (a < b && c > d) { return; }").hasErrors)
    }

    @Test fun packageManagerUseGetsApprovalNote() {
        val result = StaticCodeLinter.lint("bash", "apt install ffmpeg")
        assertFalse(result.hasErrors)
        assertTrue(result.diagnostics.any { "approval" in it.message.lowercase() })
    }
}
