package dev.quayside.core.designsystem

import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class AmTypographyTest {
    private val typography = AmTypography(ui = FontFamily.Serif, mono = FontFamily.Cursive)

    private fun spec(
        ui: String,
        terminal: String,
    ) = TypographySpec(TypographyId("test"), "Test", ui, terminal)

    @Test
    fun `known families map to bundled fonts`() {
        val mapped = PLEX_SPEC.toAmTypography()

        mapped.ui shouldBe AmFonts.family("IBM Plex Sans")
        mapped.mono shouldBe AmFonts.family("IBM Plex Mono")
        (mapped.ui == FontFamily.Default) shouldBe false
        (mapped.mono == FontFamily.Monospace) shouldBe false
    }

    @Test
    fun `unknown ui font falls back to default family`() {
        val mapped = spec(ui = "SF Pro", terminal = "IBM Plex Mono").toAmTypography()

        mapped.ui shouldBe FontFamily.Default
        mapped.mono shouldBe AmFonts.family("IBM Plex Mono")
    }

    @Test
    fun `unknown terminal font falls back to monospace`() {
        val mapped = spec(ui = "IBM Plex Sans", terminal = "Menlo").toAmTypography()

        mapped.mono shouldBe FontFamily.Monospace
        mapped.ui shouldBe AmFonts.family("IBM Plex Sans")
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TextRole::class)
    fun `code role uses mono family and the rest use ui family`(role: TextRole) {
        val expected = if (role == TextRole.CODE) FontFamily.Cursive else FontFamily.Serif

        typography.style(role).fontFamily shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TextRole::class)
    fun `title and heading are semi bold and the rest normal`(role: TextRole) {
        val expected =
            if (role == TextRole.TITLE ||
                role == TextRole.HEADING
            ) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            }

        typography.style(role).fontWeight shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TextRole::class)
    fun `styles carry no colour`(role: TextRole) {
        typography.style(role).color.isUnspecified shouldBe true
    }
}
