package dev.quayside.core.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class AmModeBarTest {
    @Test
    fun `mode bar shows badge and content`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    AmModeBar {
                        AmModeBadge("NORMAL")
                        AmText("3 agents waiting")
                    }
                }
            }

            onNodeWithText("NORMAL").assertIsDisplayed()
            onNodeWithText("3 agents waiting").assertIsDisplayed()
        }
}
