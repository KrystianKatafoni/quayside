package dev.quayside.missioncontrol.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import org.junit.jupiter.api.Test

/**
 * NFR-6: every text color of the "Nowy agent" dialog has contrast of at least 4.5:1 on the surface it is drawn on,
 * and the frame of the focused control at least 3:1 (WCAG 1.4.11). The colors are the ones the dialog uses, not
 * those of `Colors`, because `Colors.secondary` and the error tone do not reach 4.5:1 on the panel.
 */
internal class NewAgentColorsTest {
    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    @Test
    fun `NFR-6 text colors keep contrast of 4_5 to 1 on the panel`() {
        mapOf(
            "primary" to NewAgentColors.primary,
            "secondary" to NewAgentColors.secondary,
            "error" to NewAgentColors.error,
            "warning" to NewAgentColors.warning,
        ).forEach { (name, color) ->
            withClue(name) { contrast(color, NewAgentColors.panel) shouldBeGreaterThanOrEqual 4.5 }
        }
    }

    @Test
    fun `NFR-6 text in fields and buttons keeps contrast of 4_5 to 1`() {
        listOf(NewAgentColors.field, NewAgentColors.button).forEach { surface ->
            contrast(NewAgentColors.primary, surface) shouldBeGreaterThanOrEqual 4.5
        }
    }

    @Test
    fun `NFR-6 focus border keeps contrast of 3 to 1 on the panel and on the controls`() {
        listOf(NewAgentColors.panel, NewAgentColors.field, NewAgentColors.button).forEach { surface ->
            contrast(NewAgentColors.focusBorder, surface) shouldBeGreaterThanOrEqual 3.0
        }
    }
}
