package dev.quayside.notifications.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.PopupAction
import dev.quayside.notifications.api.PopupId
import dev.quayside.notifications.api.PopupModel
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupStyle
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** User scenarios of the popup cards (FR-N3). */
@OptIn(ExperimentalTestApi::class)
class PopupStackTest {
    private val actions = mutableListOf<Pair<PopupId, PopupAction>>()

    private fun state(
        vararg popups: PopupModel,
        style: PopupStyle = PopupStyle.DETAILED,
    ) = PopupUiState(popups.toList(), PopupPosition.BOTTOM_RIGHT, style)

    @Test
    fun `a card has attach, mute and dismiss that report their action`() =
        runComposeUiTest {
            setContent { PopupStack(state(aPopup("a")), { id, action -> actions += id to action }) }
            onNodeWithTag(PopupTags.attach(PopupId("a"))).assertTextEquals("Otwórz").performClick()
            onNodeWithTag(PopupTags.mute(PopupId("a"))).assertTextEquals("Wycisz 15 min").performClick()
            onNodeWithTag(PopupTags.dismiss(PopupId("a"))).assertTextEquals("Zamknij").performClick()
            actions shouldBe
                listOf(
                    PopupId("a") to PopupAction.ATTACH,
                    PopupId("a") to PopupAction.MUTE,
                    PopupId("a") to PopupAction.DISMISS,
                )
        }

    @Test
    fun `a popup without a session can only be dismissed`() =
        runComposeUiTest {
            val bell = aPopup("b", kind = NotificationKind.TERMINAL_BELL, session = null, detail = null)
            setContent { PopupStack(state(bell), { id, action -> actions += id to action }) }
            onNodeWithTag(PopupTags.attach(PopupId("b"))).assertDoesNotExist()
            onNodeWithTag(PopupTags.mute(PopupId("b"))).assertDoesNotExist()
            onNodeWithTag(PopupTags.dismiss(PopupId("b"))).assertExists()
        }

    @Test
    fun `a collapsed popup is a line with a close button`() =
        runComposeUiTest {
            setContent { PopupStack(state(aPopup("c", collapsed = true)), { id, action -> actions += id to action }) }
            onNodeWithTag(PopupTags.attach(PopupId("c"))).assertDoesNotExist()
            onNodeWithTag(PopupTags.dismiss(PopupId("c"))).assertTextEquals("✕").performClick()
            actions shouldBe listOf(PopupId("c") to PopupAction.DISMISS)
        }

    @Test
    fun `every popup of the state gets a card`() =
        runComposeUiTest {
            setContent { PopupStack(state(aPopup("a"), aPopup("b")), { _, _ -> }) }
            onNodeWithTag(PopupTags.card(PopupId("a"))).assertExists()
            onNodeWithTag(PopupTags.card(PopupId("b"))).assertExists()
        }
}
