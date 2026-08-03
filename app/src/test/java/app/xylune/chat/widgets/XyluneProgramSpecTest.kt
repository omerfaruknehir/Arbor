package app.xylune.chat.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XyluneProgramSpecTest {
    @Test fun snippetParsesAComposedQuizWithoutWidgetCapabilities() {
        val definition = XyluneProgramParser.parse(
            """{
              "schema":"xylune-snippet/1","id":"quiz","title":"Quiz","state":{"answer":"","checked":false},
              "ui":{"type":"column","children":[
                {"type":"text","text":"Pick one"},
                {"type":"choice","value":"answer","options":[{"label":"A","value":"a"},{"label":"B","value":"b"}]},
                {"type":"button","label":"Check","action":"check"}
              ]},
              "actions":{"check":[{"op":"set","target":"checked","value":true},{"op":"submit","message":"Answer {{answer}}"}]}
            }""".trimIndent(),
            XyluneProgramSurface.SNIPPET,
        ).getOrThrow()
        assertEquals("quiz", definition.id)
        assertTrue(definition.capabilities.isEmpty())
        assertEquals(setOf("column", "text", "choice", "button"), collectTypes(definition.ui))
    }

    @Test fun widgetUsesExactCapabilityManifestAndGeneralComponentTree() {
        val definition = XyluneProgramParser.parse(
            """{
              "schema":"xylune-widget/1","id":"weather","title":"Weather",
              "state":{"lat":0,"lon":0,"temperature":"—"},
              "ui":{"type":"column","children":[{"type":"metric","label":"Temperature","value":"{{temperature}}"},{"type":"button","label":"Refresh","action":"refresh"}]},
              "actions":{"refresh":[{"op":"refresh","source":"weather"}]},
              "capabilities":[
                {"type":"location","accuracy":"approximate","reason":"Find local weather"},
                {"type":"network","origins":["https://api.example.com"],"reason":"Read weather"},
                {"type":"background_refresh","reason":"Keep current"}
              ],
              "dataSources":[
                {"id":"location","type":"location","bindings":[{"state":"lat","path":"latitude"},{"state":"lon","path":"longitude"}]},
                {"id":"weather","type":"http_json","url":"https://api.example.com/current?lat={{lat}}&lon={{lon}}","bindings":[{"state":"temperature","path":"temperature","fallback":"—"}]}
              ],
              "refreshMinutes":30
            }""".trimIndent(),
            XyluneProgramSurface.WIDGET,
        ).getOrThrow()
        assertEquals(setOf("network", "location", "background_refresh"), definition.capabilities.mapTo(mutableSetOf()) { it.type })
        assertEquals(30L, definition.refreshMinutes)
    }

    @Test fun widgetRejectsUndeclaredNetworkOrigin() {
        val result = XyluneProgramParser.parse(
            """{
              "schema":"xylune-widget/1","id":"bad","state":{},
              "ui":{"type":"text","text":"x"},"actions":{},"capabilities":[],
              "dataSources":[{"id":"x","type":"http_json","url":"https://example.com/data","bindings":[{"state":"value","path":"value"}]}]
            }""".trimIndent(),
            XyluneProgramSurface.WIDGET,
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("exact network grant"))
    }

    @Test fun snippetRejectsAnyAndroidCapability() {
        val result = XyluneProgramParser.parse(
            """{"schema":"xylune-snippet/1","state":{},"ui":{"type":"text","text":"x"},"actions":{},"capabilities":[{"type":"location","reason":"No"}]}""",
            XyluneProgramSurface.SNIPPET,
        )
        assertTrue(result.isFailure)
    }

    @Test fun removedCategoryBasedAndMiniAppFormatsAreNotCompatible() {
        assertTrue(XyluneProgramParser.parse("""{"type":"counter","title":"Old"}""", XyluneProgramSurface.WIDGET).isFailure)
        assertTrue(XyluneProgramParser.parse("""{"type":"mini_app","screens":[]}""", XyluneProgramSurface.SNIPPET).isFailure)
    }

    @Test fun runtimeAppliesBoundedStateAndSubmitActionsInOrder() {
        val definition = XyluneProgramParser.parse(
            """{
              "schema":"xylune-snippet/1","id":"counter","state":{"count":2},
              "ui":{"type":"button","label":"Add","action":"add"},
              "actions":{"add":[{"op":"add","target":"count","value":3},{"op":"multiply","target":"count","value":2},{"op":"submit","message":"Count {{count}}"}]}
            }""".trimIndent(),
            XyluneProgramSurface.SNIPPET,
        ).getOrThrow()
        val transition = XyluneProgramRuntime.apply("add", definition, definition.state)
        assertEquals("10", transition.state["count"])
        assertEquals("Count 10", transition.submitMessage)
    }


    @Test fun runtimeUrlEncodesTemplateValuesForHttpQueries() {
        val rendered = XyluneProgramRuntime.render(
            "https://example.com/search?q={{urlencode:query}}&raw={{query}}",
            mapOf("query" to "İstanbul weather + rain"),
        )
        assertEquals(
            "https://example.com/search?q=%C4%B0stanbul%20weather%20%2B%20rain&raw=İstanbul weather + rain",
            rendered,
        )
    }

    @Test fun grantsAreCheckedPerWidgetInstance() {
        val definition = XyluneProgramParser.parse(
            """{
              "schema":"xylune-widget/1","id":"files","state":{},"ui":{"type":"text","text":"File"},"actions":{},
              "capabilities":[{"type":"folder","mode":"read_write","reason":"Read and update one file"},{"type":"network","origins":["https://example.com"],"reason":"Sync"}],
              "dataSources":[]
            }""".trimIndent(),
            XyluneProgramSurface.WIDGET,
        ).getOrThrow()
        assertFalse(grantsSatisfy(definition, WidgetCapabilityGrants(networkOrigins = setOf("https://example.com"), folderUri = "content://tree", folderWrite = false)))
        assertTrue(grantsSatisfy(definition, WidgetCapabilityGrants(networkOrigins = setOf("https://example.com"), folderUri = "content://tree", folderWrite = true)))
    }

    private fun collectTypes(root: XyluneProgramNode): Set<String> = buildSet {
        fun walk(node: XyluneProgramNode) { add(node.type); node.children.forEach(::walk) }
        walk(root)
    }
}
