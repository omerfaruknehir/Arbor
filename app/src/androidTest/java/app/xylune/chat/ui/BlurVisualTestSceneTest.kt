package app.xylune.chat.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.xylune.chat.ui.theme.XyluneTheme
import org.junit.Rule
import org.junit.Test

class BlurVisualTestSceneTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun stressSceneContainsMovingBackdropAndBothGlassPanels() {
        composeRule.setContent { XyluneTheme { BlurVisualTestScene() } }
        composeRule.onNodeWithTag("blur_visual_test_scene").assertExists()
        composeRule.onNodeWithTag("blur_visual_top_panel").assertExists()
        composeRule.onNodeWithTag("blur_visual_bottom_panel").assertExists()
    }
}
