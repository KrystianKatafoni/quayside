package dev.quayside.appearance.impl

import dev.quayside.appearance.api.ThemeMode
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens.rgb
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Table 3 of the AM-801 plan: dark = black panel and bg, white text, accent, warn; light = the reverse. */
class DeriveTokensTest {
    private fun row(
        mode: ThemeMode,
        bg: String,
        text: String,
        accent: String = text,
        warn: String = text,
    ): SchemeRow = SchemeRow("t", "T", mode, "$bg $bg $text $accent $warn #00FF00 #FF0000 #0000FF", 4)

    private val lightRow =
        SchemeRow("l", "L", ThemeMode.LIGHT, "#F4F6F8 #FFFFFF #2D3748 #4338CA #A16207 #15803D #B91C1C #0E7490", 4)
    private val darkRow =
        SchemeRow("d", "D", ThemeMode.DARK, "#0B0A10 #1B1A22 #E8E5F2 #A78BFA #FBBF24 #86EFAC #FCA5A5 #C4B5FD", 4)

    private val dark: ColorTokens get() = deriveTokens(row(ThemeMode.DARK, "#000000", "#FFFFFF"))
    private val light: ColorTokens get() = deriveTokens(row(ThemeMode.LIGHT, "#FFFFFF", "#000000"))

    private fun color(hex: String): Rgba = rgb(java.lang.Long.decode("0x" + hex.removePrefix("#")))

    @ParameterizedTest(name = "dark {0} is {1}")
    @CsvSource(
        "muted, #B3B3B3",
        "border, #1F1F1F",
        "border2, #414141",
        "dim, #7D7D7D",
        "raised, #0F0F0F",
        "sel, #2E2E2E",
        "warnBg, #262626",
    )
    fun `dark scheme derives mixed tokens`(
        token: String,
        expected: String,
    ) {
        pick(dark, token) shouldBe color(expected)
    }

    @ParameterizedTest(name = "light {0} is {1}")
    @CsvSource("raised, #F5F5F5", "sel, #E0E0E0")
    fun `light scheme uses weaker overlays for raised and sel`(
        token: String,
        expected: String,
    ) {
        pick(light, token) shouldBe color(expected)
    }

    @ParameterizedTest(name = "distinct colours: {0} is {1}")
    @CsvSource(
        "muted, #B3B3B3",
        "border, #1F1F1F",
        "border2, #414141",
        "dim, #7D7DB9",
        "raised, #0F0F0F",
        "sel, #2E0000",
        "warnBg, #002600",
    )
    fun `mixed tokens use the documented source colours`(
        token: String,
        expected: String,
    ) {
        // bg, panel, text, accent and warn all differ, so a wrong mix source changes the result.
        val tokens =
            deriveTokens(
                SchemeRow(
                    "t",
                    "T",
                    ThemeMode.DARK,
                    "#0000C8 #000000 #FFFFFF #FF0000 #00FF00 #00FF00 #FF0000 #0000FF",
                    4,
                ),
            )

        pick(tokens, token) shouldBe color(expected)
        tokens.terminal.termBg shouldBe color("#0000C8")
    }

    @Test
    fun `dark terminal reuses bg and text`() {
        val tokens = deriveTokens(row(ThemeMode.DARK, "#0B0A10", "#E8E5F2"))

        tokens.terminal.termBg shouldBe color("#0B0A10")
        tokens.terminal.termText shouldBe color("#E8E5F2")
    }

    @Test
    fun `light terminal is bg and text`() {
        // bg, panel, text, accent and warn all differ, so a wrong source column is visible.
        val tokens = deriveTokens(lightRow)

        tokens.terminal.termBg shouldBe color("#F4F6F8")
        tokens.terminal.termText shouldBe color("#2D3748")
        tokens.terminal shouldBe deriveTerminal(color("#F4F6F8"), color("#2D3748"), dark = false)
    }

    @Test
    fun `ansi of a light scheme is derived on bg not on panel`() {
        val onBg = deriveTerminal(color("#F4F6F8"), color("#2D3748"), dark = false).ansi
        val onPanel = deriveTerminal(color("#FFFFFF"), color("#2D3748"), dark = false).ansi

        onBg shouldNotBe onPanel
        deriveTokens(lightRow).terminal.ansi shouldBe onBg
    }

    @Test
    fun `ansi of a dark scheme is derived on bg not on panel`() {
        val tokens = deriveTokens(darkRow)
        val onBg = deriveTerminal(color("#0B0A10"), color("#E8E5F2"), dark = true).ansi
        val onPanel = deriveTerminal(color("#1B1A22"), color("#E8E5F2"), dark = true).ansi

        onBg shouldNotBe onPanel
        tokens.terminal.ansi shouldBe onBg
        tokens.terminal.termBg shouldBe color("#0B0A10")
    }

    @Test
    fun `accentInk for a yellow accent is black`() {
        deriveTokens(row(ThemeMode.DARK, "#000000", "#FFFFFF", accent = "#FFD400")).content.accentInk shouldBe
            Rgba.BLACK
    }

    @Test
    fun `warnInk for an indigo warn is white`() {
        deriveTokens(row(ThemeMode.LIGHT, "#FFFFFF", "#000000", warn = "#4338CA")).status.warnInk shouldBe
            Rgba.WHITE
    }

    @Test
    fun `the eight listed colours pass through unchanged`() {
        val tokens =
            deriveTokens(
                SchemeRow(
                    "t",
                    "T",
                    ThemeMode.DARK,
                    "#0B0A10 #100E17 #E8E5F2 #A78BFA #FBBF24 #86EFAC #FCA5A5 #C4B5FD",
                    8,
                ),
            )

        tokens.surfaces.bg shouldBe color("#0B0A10")
        tokens.surfaces.panel shouldBe color("#100E17")
        tokens.content.text shouldBe color("#E8E5F2")
        tokens.content.accent shouldBe color("#A78BFA")
        tokens.status.warn shouldBe color("#FBBF24")
        tokens.status.ok shouldBe color("#86EFAC")
        tokens.status.err shouldBe color("#FCA5A5")
        tokens.status.work shouldBe color("#C4B5FD")
    }

    @Test
    fun `high contrast dark mode uses the dark overlays`() {
        val tokens = deriveTokens(row(ThemeMode.DARK_HIGH_CONTRAST, "#000000", "#FFFFFF"))

        tokens.surfaces.raised shouldBe color("#0F0F0F")
    }

    @Test
    fun `row rejects fewer or invalid colours`() {
        shouldThrow<IllegalArgumentException> { SchemeRow("t", "T", ThemeMode.DARK, "#000000 #111111", 4) }
        shouldThrow<IllegalArgumentException> {
            SchemeRow("t", "T", ThemeMode.DARK, "#000000 #111111 #222222 #333333 #444444 #555555 #666666 nope", 4)
        }
    }

    private fun pick(
        tokens: ColorTokens,
        name: String,
    ): Rgba =
        when (name) {
            "muted" -> tokens.content.muted
            "dim" -> tokens.content.dim
            "border" -> tokens.surfaces.border
            "border2" -> tokens.surfaces.border2
            "raised" -> tokens.surfaces.raised
            "sel" -> tokens.surfaces.sel
            "warnBg" -> tokens.status.warnBg
            else -> error("unknown token $name")
        }
}
