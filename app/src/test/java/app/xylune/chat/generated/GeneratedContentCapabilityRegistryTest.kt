package app.xylune.chat.generated

import app.xylune.chat.widgets.XyluneProgramParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedContentCapabilityRegistryTest {
    @Test fun registryExposesTheProgramRuntimeExactly() {
        assertEquals(XyluneProgramParser.nodeTypes, GeneratedContentCapabilityRegistry.programNodeTypes)
        assertEquals(XyluneProgramParser.actionOps, GeneratedContentCapabilityRegistry.programActionTypes)
        assertEquals(XyluneProgramParser.capabilityTypes, GeneratedContentCapabilityRegistry.widgetCapabilityTypes)
        assertEquals(XyluneProgramParser.dataSourceTypes, GeneratedContentCapabilityRegistry.widgetDataSourceTypes)
    }

    @Test fun snippetsAndWidgetsHaveSeparateCanonicalFencesWithoutLegacyAliases() {
        assertNotNull(GeneratedContentCapabilityRegistry.capability("xylune-snippet"))
        assertNotNull(GeneratedContentCapabilityRegistry.capability("xylune-widget"))
        listOf("xylune-ui", "ui", "xylune-form", "widget", "mini_app").forEach {
            assertNull(GeneratedContentCapabilityRegistry.capability(it))
        }
    }

    @Test fun everyDocumentedFenceMapsToAValidator() {
        GeneratedContentCapabilityRegistry.fenceNames.forEach { name ->
            val capability = GeneratedContentCapabilityRegistry.capability(name)
            assertNotNull(capability)
            assertFalse(GeneratedContentCapabilityRegistry.fullSchema(capability!!.type).isBlank())
        }
    }

    @Test fun everyModelExampleValidatesSuccessfully() {
        GeneratedContentCapabilityRegistry.validExamples.forEach { (type, examples) ->
            assertTrue(examples.isNotEmpty())
            examples.forEach { source ->
                val validation = GeneratedContentCapabilityRegistry.validate(type, source)
                assertTrue("$type example failed: ${validation.summary()}", validation.valid)
            }
        }
    }

    @Test fun unsupportedFieldsAndTypesHaveDeterministicJsonPaths() {
        val invalid = GeneratedContentCapabilityRegistry.validate(GeneratedBlockType.CHART, """{"type":"recharts","script":"alert(1)","series":[]}""")
        assertEquals(
            listOf("/script:Unsupported field", "/type:Unsupported chart type: recharts", "/series:At least one series is required"),
            invalid.errors.map { "${it.path}:${it.message}" },
        )
    }

    @Test fun contractAndValidatorVersionsAreExplicit() {
        assertTrue(GeneratedContentCapabilityRegistry.CONTRACT_VERSION.startsWith("xylune-generated-content/2-"))
        assertEquals("2.4.0", GeneratedContentCapabilityRegistry.VALIDATOR_VERSION)
        assertTrue(GeneratedContentCapabilityRegistry.compactSummary().contains(GeneratedContentCapabilityRegistry.CONTRACT_VERSION))
        assertFalse(
            GeneratedContentCapabilityRegistry.contractVersionForShape(GeneratedContentCapabilityRegistry.contractShape()) ==
                GeneratedContentCapabilityRegistry.contractVersionForShape(GeneratedContentCapabilityRegistry.contractShape() + "|new-field"),
        )
    }

    @Test fun relevantPromptsKeepSnippetAndWidgetSkillsSeparate() {
        val quiz = GeneratedContentCapabilityRegistry.promptForRequest("Make a quiz inside chat")
        assertTrue(quiz.contains("`xylune-snippet` schema"))
        assertFalse(quiz.contains("`xylune-widget` schema"))
        assertTrue(quiz.contains("Xylune Home-widget skill manifest"))

        val widget = GeneratedContentCapabilityRegistry.promptForRequest("Make a live home screen widget")
        assertTrue(widget.contains("`xylune-widget` schema"))
        assertTrue(widget.contains("compile_widget"))
        assertTrue(widget.contains("{{urlencode:key}}"))
        assertTrue(widget.contains("items` are plain records"))
        assertTrue(widget.contains("cannot contain type/text/children/style"))

        val turkishWidget = GeneratedContentCapabilityRegistry.promptForRequest("Ana ekran için canlı hava durumu bileşeni yap")
        assertTrue(turkishWidget.contains("`xylune-widget` schema"))

        val continuation = GeneratedContentCapabilityRegistry.promptForConversation(
            listOf("Make a home screen widget for my habits", "Make it cleaner and add one more action"),
        )
        assertTrue(continuation.contains("`xylune-widget` schema"))

        val ordinary = GeneratedContentCapabilityRegistry.promptForRequest("Explain why the sky is blue")
        assertTrue(ordinary.startsWith(GeneratedContentCapabilityRegistry.compactSummary()))
        assertTrue(ordinary.contains("Xylune Home-widget skill manifest"))
        assertFalse(ordinary.contains("`xylune-widget` schema"))
    }
}
