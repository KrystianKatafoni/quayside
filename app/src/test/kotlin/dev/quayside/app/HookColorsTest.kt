package dev.quayside.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import org.junit.jupiter.api.Test

/** NFR-6: every text color of the hooks dialog has contrast of at least 4.5:1 on the panel. */
class HookColorsTest {
    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    @Test
    fun `NFR-6 text colors keep contrast of 4_5 to 1 on the panel`() {
        listOf(HookColors.primary, HookColors.secondary, HookColors.error).forEach {
            contrast(it, HookColors.panel) shouldBeGreaterThanOrEqual 4.5
        }
    }

    @Test
    fun `NFR-6 focus border of a button keeps contrast of 3 to 1 on the panel`() {
        contrast(HookColors.focusBorder, HookColors.panel) shouldBeGreaterThanOrEqual 3.0
    }
}
