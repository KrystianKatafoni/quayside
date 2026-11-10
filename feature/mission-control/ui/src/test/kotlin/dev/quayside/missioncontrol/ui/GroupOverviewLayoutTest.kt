package dev.quayside.missioncontrol.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Layout of a row of the group overview (FR-P6, FR-P8, AM-310 as corrected): the chips stand on the line of the
 * subfolder name, right after it (a gap of 8 dp), not at the right edge of the row. Runs at 1 px per dp in the default
 * window (1024 dp wide), so the bounds are in dp and the row is much wider than its content.
 */
@OptIn(ExperimentalTestApi::class)
internal class GroupOverviewLayoutTest {
    private val carl = MissionControlTags.overviewRow(sub("Carl"))

    private fun carlSessions() =
        listOf(
            groupSession("w1", "Carl", SessionState.AwaitingInput("q"), 1),
            groupSession("k1", "Carl", SessionState.Working, 2),
            groupSession("f1", "Carl", SessionState.Failed, 3),
        )

    /** The text node of the subfolder name (the merged row node says the name too, so it is left out). */
    private fun ComposeUiTest.nameBounds(): Rect {
        val name = hasText("Carl") and hasTestTag(carl).not() and hasAnyAncestor(hasTestTag(carl))
        waitUntil { onAllNodes(name, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        return onAllNodes(name, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
    }

    private fun ComposeUiTest.chipBounds(): List<Rect> =
        listOf("czeka: 1", "pracuje: 1", "błąd: 1").map { counterNode(carl, it).fetchSemanticsNode().boundsInRoot }

    @Test
    fun `chips stand on the line of the subfolder name`() =
        runComposeUiTest {
            // Given a subfolder with waiting, working and failed sessions
            showGroup(carlSessions())
            overviewRow("Carl")

            // When the name and the chips are laid out
            val name = nameBounds()
            val chips = chipBounds()

            // Then every chip is centred on the line of the name and starts above its bottom (not under it)
            chips.forEach { chip ->
                abs(chip.center.y - name.center.y) shouldBeLessThanOrEqualTo CENTER_TOLERANCE
                chip.top shouldBeLessThanOrEqualTo name.bottom - 1f
            }
        }

    @Test
    fun `first chip starts 8 dp after the name and far from the right edge of the row`() =
        runComposeUiTest {
            // Given the same subfolder
            showGroup(carlSessions())
            val row = overviewRow("Carl").fetchSemanticsNode().boundsInRoot

            // When the name, the chips and the row are measured
            val name = nameBounds()
            val chips = chipBounds()

            // Then the first chip is right of the name with a gap of 8 dp ...
            chips.first().left shouldBe (name.right + GAP plusOrMinus GAP_TOLERANCE)
            // ... and the last one ends far from the right edge of the row (it follows the name, it is not pushed)
            (row.right - chips.last().right) shouldBeGreaterThanOrEqualTo MIN_FREE_RIGHT
        }

    @Test
    fun `card keeps 10 dp above the first row and below the last row`() =
        runComposeUiTest {
            // Given a subfolder list of two rows
            showGroup(carlSessions())
            overviewRow("Carl")

            // When the card and its rows are measured
            val card = awaitTag(MissionControlTags.OVERVIEW).fetchSemanticsNode().boundsInRoot
            val rows = listOf("Anna", "bob", "Carl").map { overviewRow(it).fetchSemanticsNode().boundsInRoot }

            // Then the first row starts and the last one ends at least 10 dp inside the card
            (rows.minOf { it.top } - card.top) shouldBeGreaterThanOrEqualTo CARD_PADDING
            (card.bottom - rows.maxOf { it.bottom }) shouldBeGreaterThanOrEqualTo CARD_PADDING
        }

    private companion object {
        const val CARD_PADDING = 10f
        const val CENTER_TOLERANCE = 3f
        const val GAP = 8f
        const val GAP_TOLERANCE = 1.5f
        const val MIN_FREE_RIGHT = 400f
    }
}
