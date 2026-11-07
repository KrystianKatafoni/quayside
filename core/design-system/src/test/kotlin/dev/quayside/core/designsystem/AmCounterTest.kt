package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.ShapeTokens
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** Counter circle (AM-816): tone colours, circle of at least 22 dp (pixels at 4 px per dp), read by description. */
@OptIn(ExperimentalTestApi::class)
class AmCounterTest {
    private val colors = SampleTokens.colors()
    private val container = colors.surfaces.bg.argb32()

    private fun ComposeUiTest.setCounter(
        count: Int = 1,
        tone: StatusTone = StatusTone.WARN,
        shape: ShapeTokens = SampleTokens.shape(),
    ) = setContent {
        Scaled {
            AmTheme(colors, shape, PLEX_SPEC) {
                Box(Modifier.testTag(BOX).background(colors.surfaces.bg.toColor())) {
                    AmCounter(count, tone, "$count czeka")
                }
            }
        }
    }

    @Test
    fun `counter is a circle at least 22 dp`() =
        runComposeUiTest {
            setCounter(count = 3)

            val bounds = onNodeWithTag(BOX).getUnclippedBoundsInRoot()
            val pixels = onNodeWithTag(BOX).pixels()
            val fill = toneBackground(StatusTone.WARN, colors).argb32()

            bounds.width shouldBeGreaterThanOrEqualTo 22.dp
            bounds.height shouldBe bounds.width
            // the square corner shows the container, the left edge of the circle shows the tone fill
            pixels.argbAt(0, 0) shouldBe container
            pixels.argbAt(px(1), pixels.height / 2) shouldBe fill
            // 45 degrees: the circle is round, not a rounded square of a small radius
            pixels.argbAt(px(2), px(2)) shouldBe container
        }

    @Test
    fun `wide count stays at least 22 dp high and grows in width`() =
        runComposeUiTest {
            setCounter(count = 12345)

            val bounds = onNodeWithTag(BOX).getUnclippedBoundsInRoot()

            bounds.height shouldBeGreaterThanOrEqualTo 22.dp
            bounds.width shouldBeGreaterThanOrEqualTo bounds.height
        }

    @Test
    fun `counter of a square theme is square`() =
        runComposeUiTest {
            setCounter(shape = squareShape())

            onNodeWithTag(BOX).pixels().argbAt(0, 0) shouldBe toneBackground(StatusTone.WARN, colors).argb32()
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(StatusTone::class)
    fun `counter draws the tone background and label`(tone: StatusTone) =
        runComposeUiTest {
            setCounter(count = 88, tone = tone)
            val expected = chipColors(tone, colors)

            val pixels = onNodeWithTag(BOX).pixels()

            pixels.argbAt(px(1), pixels.height / 2) shouldBe expected.background.argb32()
            pixels.distinctColors() shouldContain expected.label.argb32()
        }

    @Test
    fun `counter reads its description`() =
        runComposeUiTest {
            setCounter(count = 2)

            // the number is not read on its own: one node announces the description
            onNodeWithContentDescription("2 czeka").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            onAllNodesWithText("2", useUnmergedTree = true).assertCountEquals(1)
        }

    @Test
    fun `counter is not focusable and not clickable`() =
        runComposeUiTest {
            setCounter()

            press(Key.Tab)

            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused), useUnmergedTree = true)
                .assertCountEquals(0)
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true)
                .assertCountEquals(0)
        }

    private companion object {
        const val BOX = "box"
    }
}
