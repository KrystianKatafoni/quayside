package dev.quayside.missioncontrol.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.domain.SessionId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The window "Przypisz do podfolderu…" over a ready [SessionDialog.AssignPlace] (FR-P9, AM-314): every line of the
 * list, the current one selected and focused first, a click picks, Escape and "Anuluj" dismiss. Tags hold positions.
 */
@OptIn(ExperimentalTestApi::class)
internal class PlaceDialogScreenTest {
    private val events = mutableListOf<MissionControlEvent>()

    private fun ComposeUiTest.shown(current: String) {
        val dialog = SessionDialog.AssignPlace(SessionId("sess0001"), PlaceChoices.of(OVERVIEW_GROUP), current)
        setContent { AmTestTheme { SessionDialogView(dialog) { events += it } } }
        waitUntilExactlyOneExists(hasTestTag(MissionControlTags.PLACE_DIALOG))
    }

    private fun ComposeUiTest.choice(id: String) = onNodeWithTag(PlaceTags.choice(id))

    @Test
    fun `every choice is shown and the current one is selected`() =
        runComposeUiTest {
            shown("sub.2")

            PlaceChoices.of(OVERVIEW_GROUP).forEach { choice(it.id).assertExists() }
            choice("sub.2").assertIsSelected()
            choice("auto").assertIsNotSelected()
            choice("root").assertIsNotSelected()
            onNode(hasText("Automatycznie")).assertExists()
            onNode(hasText("folder główny")).assertExists()
            onNode(hasText("Carl")).assertExists()
        }

    @Test
    fun `start focus is on the current choice and Esc closes`() =
        runComposeUiTest {
            shown("sub.2")

            waitForIdle()
            choice("sub.2").assertIsFocused()
            onNodeWithTag(MissionControlTags.PLACE_DIALOG).performKeyInput {
                keyDown(Key.Escape)
                keyUp(Key.Escape)
            }

            events shouldBe listOf(MissionControlEvent.Dismiss)
        }

    @Test
    fun `a click picks that line`() =
        runComposeUiTest {
            shown("auto")

            choice("sub.0").performClick()

            events shouldBe listOf(MissionControlEvent.PickPlace("sub.0"))
        }

    @Test
    fun `cancel dismisses and picks nothing`() =
        runComposeUiTest {
            shown("auto")

            onNodeWithTag(MissionControlTags.PLACE_CANCEL).performClick()

            events shouldBe listOf(MissionControlEvent.Dismiss)
        }

    @Test
    fun `start focus follows the current choice, whichever it is`() =
        runComposeUiTest {
            // The current line is the one that does nothing when picked, so Space or Enter at the start is harmless.
            shown("root")

            waitForIdle()
            choice("root").assertIsFocused()
            events shouldBe emptyList()
        }
}
