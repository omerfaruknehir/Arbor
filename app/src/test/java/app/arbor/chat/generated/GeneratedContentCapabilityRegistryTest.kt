package app.arbor.chat.generated

import app.arbor.chat.widgets.ArborProgramParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedContentCapabilityRegistryTest {
    @Test fun registryExposesTheProgramRuntimeExactly() {
        assertEquals(ArborProgramParser.nodeTypes, GeneratedContentCapabilityRegistry.programNodeTypes)
        assertEquals(ArborProgramParser.actionOps, GeneratedContentCapabilityRegistry.programActionTypes)
        assertEquals(ArborProgramParser.capabilityTypes, GeneratedContentCapabilityRegistry.widgetCapabilityTypes)
        assertEquals(ArborProgramParser.dataSourceTypes, GeneratedContentCapabilityRegistry.widgetDataSourceTypes)
    }

    @Test fun snippetsAndWidgetsHaveSeparateCanonicalFencesWithoutLegacyAliases() {
        assertNotNull(GeneratedContentCapabilityRegistry.capability("arbor-snippet"))
        assertNotNull(GeneratedContentCapabilityRegistry.capability("arbor-widget"))
        listOf("arbor-ui", "ui", "arbor-form", "widget", "mini_app").forEach {
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
        assertTrue(GeneratedContentCapabilityRegistry.CONTRACT_VERSION.startsWith("arbor-generated-content/2-"))
        assertEquals("2.1.0", GeneratedContentCapabilityRegistry.VALIDATOR_VERSION)
        assertTrue(GeneratedContentCapabilityRegistry.compactSummary().contains(GeneratedContentCapabilityRegistry.CONTRACT_VERSION))
        assertFalse(
            GeneratedContentCapabilityRegistry.contractVersionForShape(GeneratedContentCapabilityRegistry.contractShape()) ==
                GeneratedContentCapabilityRegistry.contractVersionForShape(GeneratedContentCapabilityRegistry.contractShape() + "|new-field"),
        )
    }

    @Test fun relevantPromptsKeepSnippetAndWidgetSkillsSeparate() {
        val quiz = GeneratedContentCapabilityRegistry.promptForRequest("Make a quiz inside chat")
        assertTrue(quiz.contains("`arbor-snippet` schema"))
        assertFalse(quiz.contains("`arbor-widget` schema"))
        assertTrue(quiz.contains("Arbor Home-widget skill manifest"))

        val widget = GeneratedContentCapabilityRegistry.promptForRequest("Make a live home screen widget")
        assertTrue(widget.contains("`arbor-widget` schema"))

        val turkishWidget = GeneratedContentCapabilityRegistry.promptForRequest("Ana ekran için canlı hava durumu bileşeni yap")
        assertTrue(turkishWidget.contains("`arbor-widget` schema"))

        val continuation = GeneratedContentCapabilityRegistry.promptForConversation(
            listOf("Make a home screen widget for my habits", "Make it cleaner and add one more action"),
        )
        assertTrue(continuation.contains("`arbor-widget` schema"))

        val ordinary = GeneratedContentCapabilityRegistry.promptForRequest("Explain why the sky is blue")
        assertTrue(ordinary.startsWith(GeneratedContentCapabilityRegistry.compactSummary()))
        assertTrue(ordinary.contains("Arbor Home-widget skill manifest"))
        assertFalse(ordinary.contains("`arbor-widget` schema"))
    }
}
