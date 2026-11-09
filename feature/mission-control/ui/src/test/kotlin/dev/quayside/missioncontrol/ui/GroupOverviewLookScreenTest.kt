package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmCounter
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

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
            rest = SessionStats(),
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
    fun `zero counter numbers are drawn muted and subfolder names as plain text`() =
        runComposeUiTest {
            show()
            awaitTag(WRAP)

            val zero = counterNode(MissionControlTags.overviewRow(sub("Anna")), "czeka: 0").argbColors()
            val name = onAllNodes(hasText("Carl"), useUnmergedTree = true).onFirst().argbColors()

            zero shouldContain colors.content.muted.argb()
            zero shouldNotContain colors.content.text.argb()
            // while the name of a subfolder is plain text (not "not muted": a 70% blend of text over panel is exactly
            // the muted colour of the sample theme, so an anti-aliased glyph edge may have that value)
            name shouldContain colors.content.text.argb()
        }

    @Test
    fun `counters use the warn work ok and err tones`() =
        runComposeUiTest {
            // Given the overview and, beside it, the reference counters of the design system on the same panel
            show {
                Row {
                    reference(REF_WARN, 1, StatusTone.WARN, "czeka: 1")
                    reference(REF_WORK, 2, StatusTone.WORK, "pracuje: 2")
                    reference(REF_OK, 3, StatusTone.OK, "gotowe: 3")
                    reference(REF_ERR, 4, StatusTone.ERR, "błąd: 4")
                }
            }
            val row = MissionControlTags.overviewRow(sub("Carl"))
            awaitTag(row)

            // Then each non-zero counter is pixel for pixel the AmCounter of its tone
            listOf("czeka: 1" to REF_WARN, "pracuje: 2" to REF_WORK, "gotowe: 3" to REF_OK, "błąd: 4" to REF_ERR)
                .forEach { (description, ref) ->
                    val counter = counterNode(row, description).pixelMap()
                    val expected = awaitTag(ref).pixelMap()
                    counter.width shouldBe expected.width
                    counter.height shouldBe expected.height
                    // (edge pixels may differ by float rounding at another x offset: at most 2 levels per channel)
                    counter.buffer.indices.all { nearly(counter.buffer[it], expected.buffer[it]) } shouldBe true
                }
            // (the four tones really differ, so a wrong tone cannot pass)
            val fills =
                listOf(REF_WARN, REF_WORK, REF_OK, REF_ERR).map {
                    awaitTag(it).pixelMap().argbAt(
                        SCALE,
                        11 * SCALE,
                    )
                }
            fills.toSet().size shouldBe 4
        }

    private fun nearly(
        a: Int,
        b: Int,
    ): Boolean =
        (0..24 step 8).all { shift ->
            kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) <= 2
        }

    @androidx.compose.runtime.Composable
    private fun reference(
        tag: String,
        count: Int,
        tone: StatusTone,
        description: String,
    ) {
        Box(Modifier.testTag(tag).background(colors.surfaces.panel.toColor())) { AmCounter(count, tone, description) }
    }

    private companion object {
        const val WRAP = "overview.wrap"
        const val REF_WARN = "ref.warn"
        const val REF_ERR = "ref.err"
        const val REF_WORK = "ref.work"
        const val REF_OK = "ref.ok"
    }
}
