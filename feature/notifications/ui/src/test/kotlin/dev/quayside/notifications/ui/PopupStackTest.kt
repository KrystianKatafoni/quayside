package dev.quayside.notifications.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.PopupAction
import dev.quayside.notifications.api.PopupId
import dev.quayside.notifications.api.PopupModel
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupStyle
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
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
            setContent { AmTestTheme { PopupStack(state(aPopup("a")), { id, action -> actions += id to action }) } }
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
            setContent { AmTestTheme { PopupStack(state(bell), { id, action -> actions += id to action }) } }
            onNodeWithTag(PopupTags.attach(PopupId("b"))).assertDoesNotExist()
            onNodeWithTag(PopupTags.mute(PopupId("b"))).assertDoesNotExist()
            onNodeWithTag(PopupTags.dismiss(PopupId("b"))).assertExists()
        }

    @Test
    fun `a collapsed popup is a line with a close button`() =
        runComposeUiTest {
            val collapsed = state(aPopup("c", collapsed = true))
            setContent { AmTestTheme { PopupStack(collapsed, { id, action -> actions += id to action }) } }
            onNodeWithTag(PopupTags.attach(PopupId("c"))).assertDoesNotExist()
            onNodeWithTag(PopupTags.dismiss(PopupId("c"))).assertTextEquals("✕").performClick()
            actions shouldBe listOf(PopupId("c") to PopupAction.DISMISS)
        }

    @Test
    fun `every popup of the state gets a card`() =
        runComposeUiTest {
            setContent { AmTestTheme { PopupStack(state(aPopup("a"), aPopup("b")), { _, _ -> }) } }
            onNodeWithTag(PopupTags.card(PopupId("a"))).assertExists()
            onNodeWithTag(PopupTags.card(PopupId("b"))).assertExists()
        }

    @Test
    fun `card bar is drawn in the token colour of its kind`() =
        runComposeUiTest {
            // Given a FAILED and a TURN_ENDED card in the plex theme
            val tokens = SampleTokens.colors()
            val failed = aPopup("f", kind = NotificationKind.FAILED)
            val ended = aPopup("e", kind = NotificationKind.TURN_ENDED)
            setContent { AmTestTheme { PopupStack(state(failed, ended), { _, _ -> }) } }

            // When the cards are captured
            fun colorsOf(popup: PopupModel): Set<Int> {
                val image = onNodeWithTag(PopupTags.card(popup.id)).captureToImage().toPixelMap()
                return (0 until image.width).flatMapTo(HashSet()) { x ->
                    (0 until image.height).map { y -> image[x, y].toArgb() }
                }
            }
            val err =
                tokens.status.err
                    .toColor()
                    .toArgb()
            val ok =
                tokens.status.ok
                    .toColor()
                    .toArgb()

            // Then the FAILED card has the err bar and no ok, and the TURN_ENDED card the reverse
            colorsOf(failed) shouldContain err
            colorsOf(failed) shouldNotContain ok
            colorsOf(ended) shouldContain ok
            colorsOf(ended) shouldNotContain err
        }
}
