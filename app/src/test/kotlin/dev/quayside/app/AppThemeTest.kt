package dev.quayside.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmFonts
import dev.quayside.core.designsystem.AmShape
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.AmTypography
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * [AppTheme] gives its content the theme of the look. `Main` wraps both of its windows in one [AppTheme], so the
 * real windows are checked by hand with `:app:run` (plan AM-806); here the content is read the way a screen reads it.
 */
@OptIn(ExperimentalTestApi::class)
class AppThemeTest {
    @Test
    fun `content reads colors shape and typography of the look`() =
        runComposeUiTest {
            // Given a look that differs from plex in every part: other accent, radius 3 with a 2 px border, Geist
            val base = SampleTokens.colors()
            val colors = base.copy(content = base.content.copy(accent = SampleTokens.rgb(0x22D3EE)))
            val shape = SampleTokens.shape(radius = 3).copy(borderWidth = 2)
            val typography = TypographySpec(TypographyId("geist"), "Geist", "Geist", "Geist Mono")
            var readColors: ColorTokens? = null
            var readShape: AmShape? = null
            var readTypography: AmTypography? = null

            // When content under AppTheme reads AmTheme
            setContent {
                AppTheme(AppLook(colors, shape, typography)) {
                    readColors = AmTheme.colors
                    readShape = AmTheme.shape
                    readTypography = AmTheme.typography
                }
            }

            // Then it sees exactly that look
            readColors shouldBe colors
            readShape?.corner shouldBe RoundedCornerShape(3.dp)
            readShape?.borderWidth shouldBe 2.dp
            readTypography?.ui shouldBe AmFonts.family("Geist")
            readTypography?.mono shouldBe AmFonts.family("Geist Mono")
        }
}
