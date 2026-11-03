package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Text colour of [AmText] inside [AmModeBar], checked on pixels. The density is 4 so that the strokes of the heavy
 * letters have fully covered pixels in the exact token colour.
 */
@OptIn(ExperimentalTestApi::class)
class AmTextColorTest {
    private val colors = SampleTokens.colors()
    private val muted = colors.content.muted.argb32()
    private val text = colors.content.text.argb32()

    @Test
    fun `colours of muted and text differ so that the checks below can tell them apart`() {
        (muted == text) shouldBe false
    }

    @Test
    fun `text in mode bar is muted`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    CompositionLocalProvider(LocalDensity provides Density(4f)) {
                        Box(Modifier.background(colors.surfaces.bg.toColor())) { AmModeBar { AmText("MMMM") } }
                    }
                }
            }

            val shown = onNodeWithText("MMMM").pixels().distinctColors()

            shown shouldContain muted
            shown shouldNotContain text
        }

    @Test
    fun `text in a panel inside mode bar is content text not muted`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    CompositionLocalProvider(LocalDensity provides Density(4f)) {
                        Box(Modifier.background(colors.surfaces.bg.toColor())) {
                            AmModeBar { AmPanel { AmText("PPPP") } }
                        }
                    }
                }
            }

            val shown = onNodeWithText("PPPP").pixels().distinctColors()

            shown shouldContain text
            shown shouldNotContain muted
        }

    @Test
    fun `text in a list row inside mode bar is content text not muted`() =
        runComposeUiTest {
            setContent {
                PlexTheme {
                    CompositionLocalProvider(LocalDensity provides Density(4f)) {
                        Box(Modifier.background(colors.surfaces.bg.toColor())) {
                            AmModeBar { AmListRow(selected = false, onClick = {}) { AmText("RRRR") } }
                        }
                    }
                }
            }

            val shown = onNodeWithText("RRRR").pixels().distinctColors()

            shown shouldContain text
            shown shouldNotContain muted
        }
}
