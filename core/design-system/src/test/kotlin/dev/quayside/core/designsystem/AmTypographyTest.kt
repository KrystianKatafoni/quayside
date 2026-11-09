package dev.quayside.core.designsystem

import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class AmTypographyTest {
    private val semiBold = setOf(TextRole.TITLE, TextRole.HEADING, TextRole.NAME, TextRole.LABEL)

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
        val expected = if (role == TextRole.CODE || role == TextRole.NAME) FontFamily.Cursive else FontFamily.Serif

        typography.style(role).fontFamily shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TextRole::class)
    fun `title heading name and label are semi bold and the rest normal`(role: TextRole) {
        val expected =
            if (role in semiBold) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            }

        typography.style(role).fontWeight shouldBe expected
    }

    @Test
    fun `name is mono semibold 15`() {
        val style = typography.style(TextRole.NAME)

        style.fontFamily shouldBe FontFamily.Cursive
        style.fontWeight shouldBe FontWeight.SemiBold
        style.fontSize shouldBe 15.sp
    }

    @Test
    fun `label is ui semibold 11 with letter spacing`() {
        val style = typography.style(TextRole.LABEL)

        style.fontFamily shouldBe FontFamily.Serif
        style.fontWeight shouldBe FontWeight.SemiBold
        style.fontSize shouldBe 11.sp
        style.letterSpacing shouldBe 0.12.em
    }

    @Test
    fun `only label has letter spacing`() {
        TextRole.entries
            .filter { it != TextRole.LABEL }
            .forEach { typography.style(it).letterSpacing.isUnspecified shouldBe true }
    }

    @Test
    fun `plex mono family bundles a semibold file for the name role`() {
        val name = PLEX_SPEC.toAmTypography().style(TextRole.NAME)

        name.fontFamily shouldBe AmFonts.family("IBM Plex Mono")
        val fonts = (name.fontFamily as FontListFontFamily).fonts
        fonts.any { it.weight == FontWeight.SemiBold } shouldBe true
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TextRole::class)
    fun `styles carry no colour`(role: TextRole) {
        typography.style(role).color.isUnspecified shouldBe true
    }
}
