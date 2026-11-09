package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.SampleTokens.rgb
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/** "Quayside" in the top bar: one text node, "Quay" in the text colour and "side" in the accent of the wordmark. */
@OptIn(ExperimentalTestApi::class)
class AmWordmarkTest {
    private val plex = SampleTokens.colors()
    private val weak = weakAccentColors()

    private fun weakAccentColors(): ColorTokens =
        plex.copy(
            surfaces = plex.surfaces.copy(bg = rgb(0x0B0A10), panel = rgb(0x100E17)),
            content = plex.content.copy(accent = rgb(0x5B4FA0)),
        )

    private fun ComposeUiTest.setWordmark(
        colors: ColorTokens = plex,
        typography: TypographySpec = PLEX_SPEC,
    ) = setContent {
        Scaled {
            AmTheme(colors, SampleTokens.shape(), typography) {
                Box(Modifier.testTag(BOX).background(colors.surfaces.panel.toColor())) { AmWordmark() }
            }
        }
    }

    private fun ComposeUiTest.textNode(): SemanticsNodeInteraction = onNodeWithTag(BOX).also { waitForIdle() }

    @Test
    fun `wordmark is one text node Quayside`() =
        runComposeUiTest {
            setWordmark()

            onAllNodes(hasText("Quayside"), useUnmergedTree = true).assertCountEquals(1)
            val node = onAllNodes(hasText("Quayside"), useUnmergedTree = true).fetchSemanticsNodes().single()
            node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } shouldBe listOf("Quayside")
        }

    @Test
    fun `wordmark draws quay in text and side in its accent`() =
        runComposeUiTest {
            setWordmark()
            val accent = plex.wordmarkAccent().argb32()
            val text = plex.content.text.argb32()
            (accent == text) shouldBe false

            val split = splitOfSide()
            val pixels = textNode().pixels()

            pixels.distinctColorsIn(0, split - MARGIN) shouldContain text
            pixels.distinctColorsIn(0, split - MARGIN) shouldNotContain accent
            pixels.distinctColorsIn(split + MARGIN, pixels.width) shouldContain accent
            pixels.distinctColorsIn(split + MARGIN, pixels.width) shouldNotContain text
        }

    @Test
    fun `wordmark side uses the corrected accent on a weak theme`() =
        runComposeUiTest {
            setWordmark(weak)
            val corrected = weak.wordmarkAccent().argb32()
            corrected shouldNotBe weak.content.accent.argb32()

            val colors = textNode().pixels().distinctColors()

            colors shouldContain corrected
            colors shouldNotContain weak.content.accent.argb32()
        }

    @Test
    fun `wordmark width does not follow the theme typography`() {
        val other = TypographySpec(TypographyId("geist"), "Geist", "Geist", "Geist Mono")
        val widths = mutableListOf<Int>()
        val bodyWidths = mutableListOf<Int>()
        listOf(PLEX_SPEC, other).forEach { typography ->
            runComposeUiTest {
                setContent {
                    AmTheme(plex, SampleTokens.shape(), typography) {
                        Box(Modifier.testTag(BOX)) { AmWordmark() }
                        Box(Modifier.testTag(BODY)) { AmText("Quayside") }
                    }
                }
                widths.add(onNodeWithTag(BOX).fetchSemanticsNode().size.width)
                bodyWidths.add(onNodeWithTag(BODY).fetchSemanticsNode().size.width)
            }
        }

        // the check is not vacuous: the body text does change with the typography
        bodyWidths[0] shouldNotBe bodyWidths[1]
        widths[0] shouldBeGreaterThan 0
        widths[0] shouldBe widths[1]
    }

    @Test
    fun `wordmark style is plex sans semibold 15`() {
        val style = wordmarkStyle()

        style.fontFamily shouldBe AmFonts.family("IBM Plex Sans")
        style.fontWeight shouldBe FontWeight(WEIGHT)
        style.fontSize shouldBe 15.sp
    }

    /** Pixel column where "side" starts, from the layout of the single text node. */
    private fun ComposeUiTest.splitOfSide(): Int {
        val node = onAllNodes(hasText("Quayside"), useUnmergedTree = true).fetchSemanticsNodes().first()
        val results = mutableListOf<TextLayoutResult>()
        checkNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)).action?.invoke(results)
        return results.first().getHorizontalPosition(QUAY_LENGTH, usePrimaryDirection = true).toInt()
    }

    private companion object {
        const val BOX = "box"
        const val BODY = "body"
        const val QUAY_LENGTH = 4
        const val MARGIN = 3
        const val WEIGHT = 600
    }
}
