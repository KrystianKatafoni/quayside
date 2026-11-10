package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmChip
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.math.abs

/**
 * Colours of the group overview (NFR-6, AM-307) read from pixels at 4 px per dp. The view is drawn alone over the
 * `bg` colour, so the card, its frame and the surroundings can be told apart (a screenshot of a clickable node
 * would not show a frame drawn outside it).
 */
@OptIn(ExperimentalTestApi::class)
internal class GroupOverviewLookScreenTest {
    private val colors = SampleTokens.colors()

    private val overview =
        GroupOverview(
            project = OVERVIEW_GROUP.id,
            rows =
                listOf(
                    SubfolderStats(sub("Carl"), SessionStats(waiting = 1, working = 2, done = 3, failed = 4)),
                    SubfolderStats(sub("Anna"), SessionStats()),
                ),
        )

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        extra: @androidx.compose.runtime.Composable () -> Unit = {},
    ) = setContent {
        AmTestTheme {
            Scaled4 {
                Column {
                    Box(Modifier.testTag(WRAP).background(colors.surfaces.bg.toColor())) {
                        GroupOverviewView(overview, onEvent = {})
                    }
                    extra()
                }
            }
        }
    }

    @Test
    fun `overview card is drawn on panel`() =
        runComposeUiTest {
            show()

            val pixels = awaitTag(WRAP).pixelMap()
            val drawn = awaitTag(WRAP).argbColors()

            // The surroundings are bg; the card is panel with a frame in the border colour
            pixels.argbAt(0, 0) shouldBe colors.surfaces.bg.argb()
            drawn shouldContain colors.surfaces.panel.argb()
            drawn shouldContain colors.surfaces.border.argb()
        }

    @Test
    fun `subfolder names are drawn as plain text and a quiet row has no chip`() =
        runComposeUiTest {
            show()
            awaitTag(WRAP)

            // (Anna, not Carl: at 4 px per dp the four chips of Carl leave its name no width in the test window)
            val name = onAllNodes(hasText("Anna"), useUnmergedTree = true).onFirst().argbColors()

            // The name of a subfolder is plain text (not "not muted": a 70% blend of text over panel is exactly the
            // muted colour of the sample theme, so an anti-aliased glyph edge may have that value)
            name shouldContain colors.content.text.argb()
            // and Anna, with nothing going on, has no chip
            counterTexts(MissionControlTags.overviewRow(sub("Anna"))) shouldBe emptyList()
        }

    @ParameterizedTest(name = "{1}")
    @MethodSource("chips")
    fun `chip of a state is drawn as the design system chip with that tone and icon`(
        ref: String,
        description: String,
    ) = runComposeUiTest {
        // Given the overview and, beside it, the reference chips of the design system on the same panel
        show {
            Row {
                REFERENCES.forEach { reference(it) }
            }
        }
        val row = MissionControlTags.overviewRow(sub("Carl"))
        awaitTag(row)

        // When the chip is drawn at 4 px per dp
        val drawn = counterNode(row, description).pixelMap()

        // Then it is pixel for pixel the AmChip of that state: tone fill, icon and number
        differences(drawn, awaitTag(ref).pixelMap()) shouldBe 0
        // (the four states are drawn differently, so the match above is not by accident)
        REFERENCES.filter { it.tag != ref }.forEach { other ->
            (differences(drawn, awaitTag(other.tag).pixelMap()) > 0) shouldBe true
        }
    }

    @Test
    fun `finished sessions get the green chip between working and failed`() =
        runComposeUiTest {
            show()
            val row = MissionControlTags.overviewRow(sub("Carl"))
            awaitTag(row)

            counterTexts(row) shouldBe listOf("czeka: 1", "pracuje: 2", "gotowe: 3", "błąd: 4")
        }

    @Test
    fun `tree guide runs unbroken from the first row to the middle of the last row`() =
        runComposeUiTest {
            show()
            val map = awaitTag(WRAP).pixelMap()
            val first = awaitTag(MissionControlTags.overviewRow(sub("Carl"))).fetchSemanticsNode().boundsInRoot
            val last = awaitTag(MissionControlTags.overviewRow(sub("Anna"))).fetchSemanticsNode().boundsInRoot
            val x = (OVERVIEW_GUIDE_X.value * SCALE).toInt()
            val guide = colors.surfaces.border2.argb()
            val lastMiddle = last.center.y.toInt()

            // Then the vertical line is one colour from the top of the first row, across the border of both rows,
            // down to the middle of the last row (an "L"); below it there is none
            (first.top.toInt()..lastMiddle - SCALE).forEach { y -> apart(map.argbAt(x, y), guide) shouldBe false }
            (lastMiddle + SCALE..last.bottom.toInt()).forEach { y -> apart(map.argbAt(x, y), guide) shouldBe true }
            // And each row has a branch from the line to 32 dp, and none beyond it
            listOf(first, last).forEach { row ->
                val y = row.center.y.toInt()
                (x until 32 * SCALE).forEach { px -> apart(map.argbAt(px, y), guide) shouldBe false }
                (34 * SCALE until 36 * SCALE).forEach { px -> apart(map.argbAt(px, y), guide) shouldBe true }
            }
        }

    /** Pixels of [a] that differ visibly from [b] (more than [TOLERANCE] in a channel; as in the sidebar test). */
    private fun differences(
        a: PixelMap,
        b: PixelMap,
    ): Int {
        if (a.width != b.width || a.height != b.height) return Int.MAX_VALUE
        return (0 until a.width).sumOf { x -> (0 until a.height).count { y -> apart(a.argbAt(x, y), b.argbAt(x, y)) } }
    }

    private fun apart(
        p: Int,
        q: Int,
    ): Boolean =
        (0..SHIFT_MAX step BYTE).any { shift -> abs(((p shr shift) and MASK) - ((q shr shift) and MASK)) > TOLERANCE }

    @androidx.compose.runtime.Composable
    private fun reference(ref: Reference) {
        Box(Modifier.testTag(ref.tag).background(colors.surfaces.panel.toColor())) {
            AmChip(label = ref.number, tone = ref.tone, icon = ref.icon, description = "ref ${ref.tag}")
        }
    }

    private class Reference(
        val tag: String,
        val number: String,
        val tone: StatusTone,
        val icon: IconKind,
    )

    private companion object {
        const val WRAP = "overview.wrap"
        const val TOLERANCE = 8
        const val BYTE = 8
        const val SHIFT_MAX = 16
        const val MASK = 0xFF

        val REFERENCES =
            listOf(
                Reference("ref.waiting", "1", StatusTone.WARN, IconKind.WAITING),
                Reference("ref.working", "2", StatusTone.WORK, IconKind.WORKING),
                Reference("ref.done", "3", StatusTone.OK, IconKind.DONE),
                Reference("ref.failed", "4", StatusTone.ERR, IconKind.FAILED),
            )

        @JvmStatic
        fun chips(): List<Arguments> =
            listOf(
                arguments("ref.waiting", "czeka: 1"),
                arguments("ref.working", "pracuje: 2"),
                arguments("ref.done", "gotowe: 3"),
                arguments("ref.failed", "błąd: 4"),
            )
    }
}
