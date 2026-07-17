package app.arbor.chat.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArborWidgetSpecTest {
    @Test fun expressionUsesNormalPrecedenceAndVariables() {
        val result = SafeExpression.evaluate("principal * (1 + rate / 100) ^ years", mapOf(
            "principal" to 100.0, "rate" to 10.0, "years" to 2.0,
        )).getOrThrow()
        assertEquals(121.0, result, 0.000001)
    }

    @Test fun expressionSupportsSafeFunctions() {
        assertEquals(10.0, SafeExpression.evaluate("max(abs(-4), round(9.7))").getOrThrow(), 0.0)
        assertEquals(0.002, SafeExpression.evaluate("2e-3").getOrThrow(), 0.0000001)
        assertTrue(SafeExpression.evaluate("unknown(2)").isFailure)
    }

    @Test fun parserReadsProgrammableForm() {
        val definition = ArborWidgetParser.parse(
            """{"type":"form","title":"Tip","fields":[{"id":"bill","kind":"number","value":50}],"outputs":[{"label":"Tip","expression":"bill*0.2","prefix":"$"}]}""",
        ).getOrThrow()
        assertEquals("bill", definition.fields.single().id)
        assertEquals("bill*0.2", definition.outputs.single().expression)
    }

    @Test fun parserKeepsLegacyChoiceWidgetsCompatible() {
        val definition = ArborWidgetParser.parse("""{"type":"choice","title":"Pick","options":["A","B"]}""").getOrThrow()
        assertEquals(listOf("A", "B"), definition.options)
        assertEquals(false, definition.homeEnabled)
    }

    @Test fun homeScreenEligibilityMustBeExplicit() {
        assertEquals(false, ArborWidgetParser.parse("""{"type":"counter","title":"Chat counter"}""").getOrThrow().homeEnabled)
        assertEquals(true, ArborWidgetParser.parse("""{"type":"counter","title":"Pinned","surface":"home"}""").getOrThrow().homeEnabled)
        assertEquals(true, ArborWidgetParser.parse("""{"type":"counter","title":"Both","surface":"both"}""").getOrThrow().homeEnabled)
        assertEquals(false, ArborWidgetParser.parse("""{"type":"counter","title":"Chat","surface":"chat","home":true}""").getOrThrow().homeEnabled)
        assertTrue(ArborWidgetParser.parse("""{"type":"counter","surface":"everywhere"}""").isFailure)
    }

    @Test fun parserRejectsExecutableFieldKinds() {
        assertTrue(ArborWidgetParser.parse("""{"type":"form","fields":[{"id":"x","kind":"javascript"}]}""").isFailure)
    }

    @Test fun parserReadsLiveStockBindings() {
        val definition = ArborWidgetParser.parse(
            """{"type":"stock","title":"ACME","symbol":"ACME","dataSource":{"url":"https://example.com/quote.json","refreshMinutes":5,"bindings":[{"id":"price","label":"Price","path":"quote.price","prefix":"$","decimals":3}]}}""",
        ).getOrThrow()
        assertEquals("ACME", definition.symbol)
        assertEquals(15, definition.dataSource!!.refreshMinutes)
        assertEquals("quote.price", definition.dataSource!!.bindings.single().path)
    }

    @Test fun parserReadsOrderedPrayerSchedule() {
        val definition = ArborWidgetParser.parse(
            """{"type":"prayer_times","title":"Times","timezone":"Europe/Istanbul","items":[{"id":"fajr","label":"Fajr","time":"05:10"},{"id":"isha","label":"Isha","time":"21:45"}]}""",
        ).getOrThrow()
        assertEquals(listOf("fajr", "isha"), definition.schedule.map { it.id })
        assertEquals("Europe/Istanbul", definition.timezone)
    }

    @Test fun parserRejectsUnsafeOrMalformedLiveDefinitions() {
        assertTrue(ArborWidgetParser.parse("""{"type":"stock","dataSource":{"url":"http://example.com","bindings":[{"id":"x","path":"x"}]}}""").isFailure)
        assertTrue(ArborWidgetParser.parse("""{"type":"live_data","dataSource":{"url":"https://example.com","bindings":[{"id":"x","path":"x;exec()"}]}}""").isFailure)
        assertTrue(ArborWidgetParser.parse("""{"type":"schedule","items":[{"label":"Bad","time":"25:90"}]}""").isFailure)
    }

    @Test fun parserReadsMultiScreenNativeMiniApp() {
        val definition = ArborWidgetParser.parse(
            """{
              "type":"mini_app","title":"Budget cockpit","state":{"income":5000,"spent":1200,"currency":"USD"},
              "screens":[
                {"id":"dashboard","title":"Dashboard","components":[
                  {"type":"metric","id":"remaining","label":"Remaining","expression":"income-spent","prefix":"$"},
                  {"type":"progress","id":"spent","label":"Budget used","max":5000},
                  {"type":"buttons","id":"quick","buttons":[{"label":"Add expense","actions":[{"operation":"add","target":"spent","value":25}]}]},
                  {"type":"chart","id":"chart","value":"donut","items":[{"label":"Spent","value":"{{spent}}"},{"label":"Remaining","value":"{{=income-spent}}"}]}
                ]},
                {"id":"settings","title":"Settings","components":[{"type":"input","id":"income","label":"Income","value":"number"}]}
              ]
            }""".trimIndent(),
        ).getOrThrow()
        assertEquals(2, definition.miniApp!!.screens.size)
        assertEquals(listOf("metric", "progress", "buttons", "chart"), definition.miniApp!!.screens.first().components.map { it.type })
    }

    @Test fun miniAppRuntimeAppliesActionChainsAndNavigation() {
        val defaults = mapOf("count" to "2", "total" to "0", ArborMiniAppRuntime.SCREEN_STATE to "main")
        val transition = ArborMiniAppRuntime.apply(
            listOf(
                ArborMiniAppAction("add", target = "count", value = "3"),
                ArborMiniAppAction("evaluate", target = "total", expression = "count*10"),
                ArborMiniAppAction("navigate", screen = "details"),
                ArborMiniAppAction("submit", message = "Total is {{total}}"),
            ),
            defaults,
            defaults,
        )
        assertEquals("5", transition.state["count"])
        assertEquals("50", transition.state["total"])
        assertEquals("details", transition.state[ArborMiniAppRuntime.SCREEN_STATE])
        assertEquals("Total is 50", transition.submitMessage)
    }

    @Test fun miniAppConditionsAndTemplatesStayDeclarative() {
        val state = mapOf("enabled" to "true", "mode" to "advanced", "amount" to "12")
        assertTrue(ArborMiniAppRuntime.visible("enabled", state))
        assertTrue(ArborMiniAppRuntime.visible("mode==advanced", state))
        assertEquals("Result 24", ArborMiniAppRuntime.render("Result {{=amount*2}}", state))
    }

    @Test fun miniAppRejectsUnknownCodeLikeComponentsAndScreens() {
        assertTrue(ArborWidgetParser.parse(
            """{"type":"mini_app","screens":[{"id":"main","components":[{"type":"webview","id":"x"}]}]}""",
        ).isFailure)
        assertTrue(ArborWidgetParser.parse(
            """{"type":"mini_app","screens":[{"id":"main","components":[{"type":"buttons","id":"x","buttons":[{"label":"Go","actions":[{"operation":"navigate","screen":"missing"}]}]}]}]}""",
        ).isFailure)
    }
}
