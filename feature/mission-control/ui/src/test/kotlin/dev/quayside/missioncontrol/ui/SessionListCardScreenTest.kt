package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/** The session list as one card (AM-816), read from pixels at 1 px per dp: card radius, separator, segment radius. */
@OptIn(ExperimentalTestApi::class)
internal class SessionListCardScreenTest {
    private val colors = SampleTokens.colors()

    private fun snapshot() =
        freshSnapshot(
            aSession(id = "a", project = MC_PROJECT.id, state = SessionState.Working),
            aSession(id = "b", project = MC_PROJECT.id, state = SessionState.Done),
        )

    @Test
    fun `list card has the card radius`() =
        runComposeUiTest {
            // The rows alone at 4 px per dp (the whole screen does not fit the test window at that scale)
            setContent {
                AmTestTheme {
                    Scaled4 {
                        Box(Modifier.background(colors.surfaces.bg.toColor())) {
                            SessionRows(listOf(row("a"), row("b")), null, {}, Modifier)
                        }
                    }
                }
            }
            val pixels = awaitTag(MissionControlTags.LIST_CARD).pixelMap()

            // plex: r 8, card radius r + 4 = 12 dp (48 px): the corner begins 0.29 radius in along the diagonal
            pixels.argbAt(0, 0) shouldBe colors.surfaces.bg.argb()
            pixels.diagonalOnset(colors.surfaces.bg.argb()).shouldNotBeNull().shouldBeInRange(13..15)
        }

    private fun row(id: String) =
        SessionRow(
            SessionId(id),
            StatusLook.of(SessionState.Working),
            id,
            null,
            UiText("event.working"),
            UiText("age.now"),
            selected = false,
        )

    @Test
    fun `a separator in the border colour runs between the rows`() =
        runComposeUiTest {
            showScripted(snapshot())
            val card = awaitTag(MissionControlTags.LIST_CARD)
            val rows =
                listOf(
                    "a",
                    "b",
                ).map { awaitTag(MissionControlTags.row(SessionId(it))).fetchSemanticsNode().boundsInRoot }
            val first = rows.minBy { it.top }
            val pixels = card.pixelMap()
            val top = card.fetchSemanticsNode().boundsInRoot.top

            val y = (first.bottom - top).toInt()

            pixels.argbAt(pixels.width / 2, y) shouldBe colors.surfaces.border.argb()
            // and not between the card edge and the first row
            pixels.argbAt(pixels.width / 2, (first.top - top).toInt() + 1) shouldBe colors.surfaces.panel.argb()
        }

    @Test
    fun `selected segment is clipped to the row radius`() =
        runComposeUiTest {
            showScripted(snapshot())
            awaitTag(MissionControlTags.filter(SessionFilter.ACTIVE)).performClick()
            waitForIdle()

            val pixels = awaitTag(MissionControlTags.filter(SessionFilter.ACTIVE)).pixelMap()
            val raised = colors.surfaces.raised.argb()

            // the neighbours are raised, the segment is filled and its corner is cut with the row radius (10 dp)
            pixels.argbAt(2, 2) shouldBe raised
            // (the click leaves the hover overlay on sel, so the middle is "not raised" rather than exactly sel)
            pixels.argbAt(pixels.width / 2, 4) shouldNotBe raised
        }
}
