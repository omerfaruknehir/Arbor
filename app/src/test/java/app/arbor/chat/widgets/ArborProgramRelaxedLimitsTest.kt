package app.arbor.chat.widgets

import org.junit.Assert.assertTrue
import org.junit.Test

class ArborProgramRelaxedLimitsTest {
    @Test
    fun widgetInputNodeIsAcceptedAsDisplayControl() {
        val source = """{
          "schema":"arbor-widget/1",
          "id":"input_widget",
          "title":"Input",
          "state":{"name":"Arbor"},
          "ui":{"type":"input","value":"name","label":"Name","action":"open"},
          "actions":{"open":[{"op":"open_app","route":"memory"}]}
        }""".trimIndent()
        assertTrue(ArborProgramParser.parse(source, ArborProgramSurface.WIDGET).isSuccess)
    }

    @Test
    fun widgetCanContainMoreThanLegacySixListRows() {
        val items = (1..20).joinToString(",") { "{\"label\":\"Row $it\",\"value\":\"$it\"}" }
        val source = """{
          "schema":"arbor-widget/1",
          "id":"long_list",
          "title":"List",
          "state":{},
          "ui":{"type":"list","items":[$items]}
        }""".trimIndent()
        val parsed = ArborProgramParser.parse(source, ArborProgramSurface.WIDGET).getOrThrow()
        assertTrue(parsed.ui.items.size == 20)
    }
}
