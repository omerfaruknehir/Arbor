package app.arbor.chat.generated

import app.arbor.chat.widgets.ArborMiniAppParser
import app.arbor.chat.widgets.ArborWidgetParser
import app.arbor.chat.widgets.renderedMiniAppComponentTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedContentCapabilityRegistryTest {
    @Test fun everyRegisteredMiniAppComponentHasValidatorAndRenderer() {
        assertEquals(ArborMiniAppParser.supportedComponentTypes, GeneratedContentCapabilityRegistry.miniAppComponentTypes)
        assertEquals(GeneratedContentCapabilityRegistry.miniAppComponentTypes, renderedMiniAppComponentTypes)
    }

    @Test fun everyRegisteredActionIsValidatedByRuntimeParser() {
        assertEquals(ArborMiniAppParser.supportedOperations, GeneratedContentCapabilityRegistry.miniAppActionTypes)
        assertTrue(GeneratedContentCapabilityRegistry.widgetTypes.containsAll(ArborWidgetParser.supportedTypes))
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
        assertTrue(GeneratedContentCapabilityRegistry.CONTRACT_VERSION.startsWith("arbor-generated-content/1-"))
        assertEquals("1.0.0", GeneratedContentCapabilityRegistry.VALIDATOR_VERSION)
        assertTrue(GeneratedContentCapabilityRegistry.compactSummary().contains(GeneratedContentCapabilityRegistry.CONTRACT_VERSION))
        assertFalse(
            GeneratedContentCapabilityRegistry.contractVersionForShape(GeneratedContentCapabilityRegistry.contractShape()) ==
                GeneratedContentCapabilityRegistry.contractVersionForShape(GeneratedContentCapabilityRegistry.contractShape() + "|new-field"),
        )
    }

    @Test fun relevantPromptsIncludeOnlyRequestedFullSchema() {
        val chart = GeneratedContentCapabilityRegistry.promptForRequest("Make a bar chart for this data")
        assertTrue(chart.contains("`arbor-chart` schema"))
        assertFalse(chart.contains("`arbor-widget` Home-screen schema"))

        val ordinary = GeneratedContentCapabilityRegistry.promptForRequest("Explain why the sky is blue")
        assertEquals(GeneratedContentCapabilityRegistry.compactSummary(), ordinary)
        assertFalse(ordinary.contains("Exact valid examples:"))
    }
}
