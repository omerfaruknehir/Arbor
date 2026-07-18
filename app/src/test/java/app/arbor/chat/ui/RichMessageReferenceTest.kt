package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichMessageReferenceTest {
    @Test fun sourceNotationBecomesAnArborSourceLink() {
        val rendered = prepareReferenceMarkdown(
            "Claim [[source|Android docs|https://developer.android.com/guide]]",
        )
        assertTrue(rendered.contains("[Android docs](arbor-source://reference?target="))
        assertTrue(rendered.contains("https%3A%2F%2Fdeveloper.android.com%2Fguide"))
        assertFalse(rendered.contains("[[source|"))
    }

    @Test fun fileNotationBecomesAnArborFileLink() {
        val rendered = prepareReferenceMarkdown("See [[file|Build log|logs/build output.txt]]")
        assertTrue(rendered.contains("[Build log](arbor-file://reference?target="))
        assertTrue(rendered.contains("logs%2Fbuild%20output.txt"))
    }

    @Test fun ordinaryMarkdownLinksArePreservedForPreviewInterception() {
        val link = "[Example](https://example.com/path)"
        assertTrue(prepareReferenceMarkdown(link).contains(link))
    }
}
