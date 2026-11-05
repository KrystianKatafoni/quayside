package dev.quayside.core.designsystem

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.SampleTokens
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class AmThemeTest {
    @Test
    fun `content reads colors shape and typography of AmTheme`() =
        runComposeUiTest {
            // Given a theme with the plex colours, square corners and the IBM Plex typography
            var colors: ColorTokens? = null
            var shape: AmShape? = null
            var typography: AmTypography? = null

            // When content reads the theme
            setContent {
                AmTheme(SampleTokens.colors(), squareShape(), PLEX_SPEC) {
                    colors = AmTheme.colors
                    shape = AmTheme.shape
                    typography = AmTheme.typography
                }
            }
            waitForIdle()

            // Then it gets the values it was given
            colors shouldBe SampleTokens.colors()
            shape shouldBe squareShape().toAmShape()
            typography?.ui shouldBe AmFonts.family("IBM Plex Sans")
            typography?.mono shouldBe AmFonts.family("IBM Plex Mono")
        }

    @Test
    fun `nested AmTheme overrides outer values`() =
        runComposeUiTest {
            // Given a plex theme with a nested paper theme with another shape and typography
            var outer: Triple<ColorTokens, AmShape, AmTypography>? = null
            var inner: Triple<ColorTokens, AmShape, AmTypography>? = null
            val monoSpec = PLEX_SPEC.copy(uiFont = "JetBrains Mono", terminalFont = "JetBrains Mono")

            // When both levels read the theme
            setContent {
                AmTheme(SampleTokens.colors(), SampleTokens.shape(), PLEX_SPEC) {
                    outer = Triple(AmTheme.colors, AmTheme.shape, AmTheme.typography)
                    AmTheme(paperColors(), squareShape(), monoSpec) {
                        inner = Triple(AmTheme.colors, AmTheme.shape, AmTheme.typography)
                    }
                }
            }
            waitForIdle()

            // Then the inner values win inside and the outer ones are intact outside
            outer?.first shouldBe SampleTokens.colors()
            outer?.second shouldBe SampleTokens.shape().toAmShape()
            outer?.third?.ui shouldBe AmFonts.family("IBM Plex Sans")
            inner?.first shouldBe paperColors()
            inner?.second shouldBe squareShape().toAmShape()
            inner?.third?.ui shouldBe AmFonts.family("JetBrains Mono")
        }

    @Test
    fun `theme change recomposes readers with the new values`() =
        runComposeUiTest {
            var tokens by mutableStateOf(SampleTokens.colors())
            var seen: ColorTokens? = null
            setContent {
                AmTheme(tokens, SampleTokens.shape(), PLEX_SPEC) { seen = AmTheme.colors }
            }
            waitForIdle()

            tokens = paperColors()
            waitForIdle()

            seen shouldBe paperColors()
        }

    @Test
    fun `density defaults to comfortable`() =
        runComposeUiTest {
            var density: AmDensity? = null

            setContent {
                AmTheme(SampleTokens.colors(), SampleTokens.shape(), PLEX_SPEC) { density = AmTheme.density }
            }
            waitForIdle()

            density shouldBe AmDensity.COMFORTABLE
        }

    @Test
    fun `nested AmTheme overrides the density`() =
        runComposeUiTest {
            // Given a compact outer theme with a comfortable one nested in it
            var outer: AmDensity? = null
            var inner: AmDensity? = null

            setContent {
                AmTheme(SampleTokens.colors(), SampleTokens.shape(), PLEX_SPEC, AmDensity.COMPACT) {
                    outer = AmTheme.density
                    AmTheme(SampleTokens.colors(), SampleTokens.shape(), PLEX_SPEC, AmDensity.COMFORTABLE) {
                        inner = AmTheme.density
                    }
                }
            }
            waitForIdle()

            outer shouldBe AmDensity.COMPACT
            inner shouldBe AmDensity.COMFORTABLE
        }

    @Test
    fun `density change recomposes readers`() =
        runComposeUiTest {
            var density by mutableStateOf(AmDensity.COMFORTABLE)
            var seen: AmDensity? = null
            setContent {
                AmTheme(SampleTokens.colors(), SampleTokens.shape(), PLEX_SPEC, density) { seen = AmTheme.density }
            }
            waitForIdle()

            density = AmDensity.COMPACT
            waitForIdle()

            seen shouldBe AmDensity.COMPACT
        }

    @Test
    fun `reading AmTheme outside provider fails with clear message`() =
        runComposeUiTest {
            val failure =
                runCatching {
                    setContent { AmTheme.colors }
                    waitForIdle()
                }.exceptionOrNull()

            val messages = generateSequence(failure) { it.cause }.mapNotNull { it.message }.toList()
            messages.any { it.contains("AmTheme is missing") } shouldBe true
        }

    @Test
    fun `variable font semi bold renders wider than regular`() =
        runComposeUiTest {
            // Given the same text in the variable IBM Plex Sans at weight 400 and 600
            val family = checkNotNull(AmFonts.family("IBM Plex Sans")) { "IBM Plex Sans is not bundled" }
            setContent {
                sample("regular", family, FontWeight.Normal)
                sample("semibold", family, FontWeight.SemiBold)
            }
            waitForIdle()

            // Then the wght axis is applied: the semi bold line is wider
            val regular = onNodeWithTag("regular").fetchSemanticsNode().size.width
            val semiBold = onNodeWithTag("semibold").fetchSemanticsNode().size.width
            semiBold shouldBeGreaterThan regular
        }

    @Composable
    private fun sample(
        tag: String,
        family: FontFamily,
        weight: FontWeight,
    ) {
        BasicText(
            text = "Quayside agents waiting for you",
            modifier = Modifier.testTag(tag),
            style = TextStyle(fontFamily = family, fontWeight = weight, fontSize = 20.sp),
        )
    }
}
