package app.xylune.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalDocumentsConsistencyTest {
    private val repositoryRoot = File("..")

    @Test
    fun `published legal pages mirror repository documents`() {
        assertSiteMirror("PRIVACY.md", "docs/privacy/index.md")
        assertSiteMirror("TERMS.md", "docs/terms/index.md")
        assertSiteMirror("DATA_DELETION.md", "docs/data-deletion/index.md")
    }

    @Test
    fun `legal boundary does not imply a hosted service or maintainer data access`() {
        val privacy = repositoryRoot.resolve("PRIVACY.md").readText()
        val terms = repositoryRoot.resolve("TERMS.md").readText()
        val deletion = repositoryRoot.resolve("DATA_DELETION.md").readText()

        assertTrue(
            privacy.contains("This is a factual privacy notice, not a contract or a request for consent"),
        )
        assertTrue(privacy.contains("does not receive, collect, store, or have technical access"))
        assertTrue(
            privacy.contains(
                "[GitHub](https://docs.github.com/site-policy/privacy-policies/github-privacy-statement)" +
                    "—not the Xylune maintainer—operates",
            ),
        )
        assertTrue(
            terms.contains(
                "open-source client software—not a hosted AI, cloud, account, or support service",
            ),
        )
        assertTrue(terms.contains("Apache License 2.0"))
        assertFalse(terms.contains("indemn", ignoreCase = true))
        assertFalse(terms.contains("cap is currently", ignoreCase = true))
        assertFalse(terms.contains("EUR 10"))
        assertTrue(deletion.contains("Do not put a privacy request"))
        assertTrue("Privacy notice should stay concise", privacy.lines().size <= 120)
        assertTrue("Terms should stay concise", terms.lines().size <= 90)
    }

    private fun assertSiteMirror(sourcePath: String, sitePath: String) {
        val source = repositoryRoot.resolve(sourcePath).readText()
        val site = repositoryRoot.resolve(sitePath).readText()
        val siteBody = site.substringAfter("\n---\n\n", missingDelimiterValue = "")

        assertTrue("$sitePath must contain Jekyll front matter", siteBody.isNotEmpty())
        assertEquals("$sitePath must mirror $sourcePath", source, siteBody)
    }
}
