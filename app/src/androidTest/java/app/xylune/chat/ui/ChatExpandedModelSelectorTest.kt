package app.xylune.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import app.xylune.chat.ui.theme.XyluneTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class ChatExpandedModelSelectorTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun expandedModelSelectorReceivesPhysicalTap() {
        val clickCount = AtomicInteger(0)

        composeRule.setContent {
            XyluneTheme {
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                    rememberTopAppBarState(),
                )
                val blurState = rememberXyluneBackdropBlurState()
                Box(Modifier.fillMaxSize()) {
                    ChatCollapsingTranslucentTopBar(
                        title = "Expanded title",
                        scrollBehavior = scrollBehavior,
                        navigationIcon = { Spacer(Modifier.size(48.dp)) },
                        actions = {},
                        modelSelector = {
                            Surface(
                                onClick = { clickCount.incrementAndGet() },
                                modifier = Modifier.testTag("expanded_model_selector"),
                            ) {
                                Text("Choose model")
                            }
                        },
                        blurState = blurState,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("expanded_model_selector").performTouchInput { click() }

        composeRule.runOnIdle { assertEquals(1, clickCount.get()) }
    }
}
