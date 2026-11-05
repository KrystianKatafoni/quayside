package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** Chip pixels at 4 px per dp; the label "MMMM" has heavy strokes with fully covered pixels. */
@OptIn(ExperimentalTestApi::class)
class AmChipTest {
    private val plex = SampleTokens.colors()
    private val paper = paperColors()

    private fun ComposeUiTest.setChip(
        tone: StatusTone,
        tokens: ColorTokens = plex,
        icon: IconKind? = null,
        description: String? = null,
        label: String = "MMMM",
    ) = setContent {
        Scaled {
            AmTheme(tokens, SampleTokens.shape(), PLEX_SPEC) {
                Box(Modifier.testTag(BOX).background(tokens.surfaces.bg.toColor())) {
                    AmChip(label, tone = tone, icon = icon, description = description)
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(StatusTone::class)
    fun `chip draws background label and icon in chip colours`(tone: StatusTone) =
        runComposeUiTest {
            setChip(tone, icon = IconKind.PLUS)
            val expected = chipColors(tone, plex)

            val pixels = onNodeWithTag(BOX).pixels()
            val shown = pixels.distinctColors()

            // top edge, middle: inside the pill and above the text
            pixels.argbAt(pixels.width / 2, px(1)) shouldBe expected.background.argb32()
            shown shouldContain expected.label.argb32()
            shown shouldContain expected.mark.argb32()
        }

    @Test
    fun `neutral chip without icon draws no mark colour`() =
        runComposeUiTest {
            setChip(StatusTone.NEUTRAL, icon = null)
            val withoutIcon = onNodeWithTag(BOX).pixels().distinctColors()

            // Given the same chip without an icon: no mark colour, only the label colour
            withoutIcon shouldContain plex.content.text.argb32()
            withoutIcon shouldNotContain plex.content.muted.argb32()
        }

    @Test
    fun `neutral chip with icon draws the mark colour`() =
        runComposeUiTest {
            setChip(StatusTone.NEUTRAL, icon = IconKind.PLUS)

            onNodeWithTag(BOX).pixels().distinctColors() shouldContain plex.content.muted.argb32()
        }

    @Test
    fun `chip label falls back to text on paper warn`() =
        runComposeUiTest {
            // Given paper, where warn on its own background is below 4.5:1 (checked by ToneColorsTest)
            setChip(StatusTone.WARN, tokens = paper, icon = null)

            val pixels = onNodeWithTag(BOX).pixels()
            val shown = pixels.distinctColors()

            pixels.argbAt(pixels.width / 2, px(1)) shouldBe paper.status.warnBg.argb32()
            shown shouldContain paper.content.text.argb32()
            shown shouldNotContain paper.status.warn.argb32()
        }

    @Test
    fun `description replaces label for screen readers`() =
        runComposeUiTest {
            setChip(StatusTone.WARN, description = "2 sessions waiting", label = "2")

            onNodeWithContentDescription("2 sessions waiting")
                .assertExists()
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        }

    @Test
    fun `chip without description is read by its label`() =
        runComposeUiTest {
            setChip(StatusTone.OK, label = "Done")

            onNodeWithText("Done")
                .assertExists()
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        }

    @Test
    fun `chip is not focusable`() =
        runComposeUiTest {
            setChip(StatusTone.OK, label = "Done")

            press(Key.Tab)

            onNodeWithText("Done")
                .assert(
                    SemanticsMatcher("is not focused") {
                        it.config.getOrElse(SemanticsProperties.Focused) { false } !=
                            true
                    },
                ).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        }

    private companion object {
        const val BOX = "box"
    }
}
