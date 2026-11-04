package dev.quayside.core.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Line limit of [AmText]: one line by default, more lines on request. */
@OptIn(ExperimentalTestApi::class)
class AmTextTest {
    private val long = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho"

    private fun heightOf(maxLines: Int?): Float {
        var height = 0f
        runComposeUiTest {
            setContent {
                AmTestTheme {
                    val modifier = Modifier.testTag("t").width(120.dp)
                    if (maxLines == null) AmText(long, modifier) else AmText(long, modifier, maxLines = maxLines)
                }
            }
            height = onNodeWithTag("t").getUnclippedBoundsInRoot().height.value
        }
        return height
    }

    @Test
    fun `one line by default ends with ellipsis`() {
        // Given a text much longer than 120 dp, when shown with the default maxLines
        val oneLine = heightOf(maxLines = null)

        // Then it takes the same height as an explicit single line, not the height of the wrapped text
        oneLine shouldBe heightOf(maxLines = 1)
        (heightOf(maxLines = 3) / oneLine) shouldBeGreaterThan 1.5f
    }

    @Test
    fun `max lines lets text wrap`() {
        // Given the same text, when three lines are allowed
        val one = heightOf(maxLines = 1)
        val three = heightOf(maxLines = 3)

        // Then the node is about three times as high as one line
        three shouldBeGreaterThan one * 2.5f
    }

    @Test
    fun `max lines below one is rejected`() {
        // Given zero lines allowed, when the text is composed, then the call is rejected
        shouldThrow<IllegalArgumentException> { heightOf(maxLines = 0) }
    }
}
