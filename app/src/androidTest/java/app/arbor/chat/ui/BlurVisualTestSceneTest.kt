package app.arbor.chat.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.arbor.chat.ui.theme.ArborTheme
import org.junit.Rule
import org.junit.Test

class BlurVisualTestSceneTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun stressSceneContainsMovingBackdropAndBothGlassPanels() {
        composeRule.setContent { ArborTheme { BlurVisualTestScene() } }
        composeRule.onNodeWithTag("blur_visual_test_scene").assertExists()
        composeRule.onNodeWithTag("blur_visual_top_panel").assertExists()
        composeRule.onNodeWithTag("blur_visual_bottom_panel").assertExists()
    }
}
