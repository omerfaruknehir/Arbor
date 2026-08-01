package app.arbor.chat.widgets

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.arbor.chat.generated.GeneratedBlockCompiler
import app.arbor.chat.generated.GeneratedBlockType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetProgramCompilerTest {
    @Test fun simpleCounterCompilesBeforeDisplay() = runBlocking {
        val source = """{
          "schema":"arbor-widget/1",
          "id":"counter",
          "title":"Counter",
          "state":{"count":0},
          "ui":{"type":"column","children":[
            {"type":"metric","label":"Count","value":"{{count}}"},
            {"type":"button","label":"Add one","action":"increment"}
          ]},
          "actions":{"increment":[{"op":"add","target":"count","value":1}]},
          "capabilities":[],
          "dataSources":[]
        }""".trimIndent()

        val result = GeneratedBlockCompiler(ApplicationProvider.getApplicationContext()).compile(GeneratedBlockType.HOME_WIDGET, source)
        assertTrue(result.errors.joinToString("\n") { "${it.phase} ${it.path}: ${it.message}" }, result.errors.isEmpty())
    }

    @Test fun unreadableTextIsRejectedByCompiler() = runBlocking {
        val source = """{
          "schema":"arbor-widget/1",
          "id":"tiny",
          "title":"Tiny",
          "state":{},
          "ui":{"type":"text","text":"Unreadable","style":{"fontSize":10}},
          "actions":{},
          "capabilities":[],
          "dataSources":[]
        }""".trimIndent()

        val result = GeneratedBlockCompiler(ApplicationProvider.getApplicationContext()).compile(GeneratedBlockType.HOME_WIDGET, source)
        assertTrue(result.errors.any { it.phase == "layout_compile" && it.path.endsWith("fontSize") })
    }
}
