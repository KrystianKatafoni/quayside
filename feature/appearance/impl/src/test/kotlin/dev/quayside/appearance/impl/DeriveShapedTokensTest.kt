package dev.quayside.appearance.impl

import dev.quayside.appearance.api.ThemeMode
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Table 3 of the AM-805 plan, plus one dark row whose bg, panel, text, muted, accent and border all differ. */
class DeriveShapedTokensTest {
    private fun light(): ColorTokens =
        deriveShapedTokens(
            ShapedRow(
                "t",
                "T",
                ThemeMode.LIGHT,
                "#F0F0F0 #FFFFFF/60 #000000 #808080 #3182CE #000000/5",
                "1 8 none",
                "plex",
            ),
        )

    private fun midnight(): ColorTokens =
        deriveShapedTokens(
            ShapedRow(
                "t",
                "T",
                ThemeMode.DARK,
                "#0B0F19 #111827 #F3F4F6 #9CA3AF #3B82F6 #1F2937",
                "1 6 none",
                "plex",
            ),
        )

    @Test
    fun `translucent panel keeps its alpha and the overlays use the panel composited on bg`() {
        val tokens = light()

        tokens.surfaces.panel shouldBe Rgba(0x99FFFFFFL)
        tokens.surfaces.raised shouldBe hex("#EFEFEF")
        tokens.surfaces.sel shouldBe hex("#E1EBF4")
        tokens.status.warnBg shouldBe hex("#EAE1D5")
    }

    @Test
    fun `light theme corrects muted and derives dim, border2 and the fixed terminal pair`() {
        val tokens = light()

        // #808080 is corrected for bg, panel, sel and warnBg; #6D6D6D would pass on bg and panel only
        tokens.content.muted shouldBe hex("#656565")
        tokens.content.dim shouldBe hex("#8F8F8F")
        tokens.surfaces.border shouldBe Rgba.BLACK.withAlpha(5)
        tokens.surfaces.border2 shouldBe Rgba.BLACK.withAlpha(5)
        tokens.terminal.termBg shouldBe hex("#1E242C")
        tokens.terminal.termText shouldBe hex("#E2E8F0")
    }

    @Test
    fun `listed colours pass through and status comes from deriveStatus`() {
        val tokens = light()

        tokens.surfaces.bg shouldBe hex("#F0F0F0")
        tokens.content.text shouldBe hex("#000000")
        tokens.content.accent shouldBe hex("#3182CE")
        // The candidates A16207, 15803D and B91C1C are darkened as far as 4.5:1 on bg, panel and sel needs; work is the
        // accent darkened the same way
        tokens.status.work shouldBe hex("#296CAB")
        listOf(tokens.status.warn, tokens.status.ok, tokens.status.err) shouldBe
            listOf(hex("#975C07"), hex("#147839"), hex("#B91C1C"))
    }

    @Test
    fun `dark theme with black panel and white muted`() {
        val tokens =
            deriveShapedTokens(
                ShapedRow(
                    "t",
                    "T",
                    ThemeMode.DARK,
                    "#000000 #000000 #FFFFFF #FFFFFF #FE8019 #333333",
                    "1 0 none",
                    "plex",
                ),
            )

        tokens.surfaces.raised shouldBe hex("#0F0F0F")
        tokens.surfaces.sel shouldBe hex("#2E1705")
        tokens.status.warnBg shouldBe hex("#0E2320")
        tokens.content.muted shouldBe hex("#FFFFFF")
        tokens.status.warn shouldBe hex("#5EEAD4")
        tokens.status.ok shouldBe hex("#A3E635")
        tokens.status.err shouldBe hex("#FB7185")
        tokens.status.work shouldBe hex("#FE8019")
        tokens.terminal.termBg shouldBe hex("#000000")
    }

    @Test
    fun `dark overlays mix from panel with text, accent and warn`() {
        val tokens = midnight()

        tokens.surfaces.raised shouldBe hex("#1F2533")
        tokens.surfaces.sel shouldBe hex("#192B4C")
        tokens.status.warnBg shouldBe hex("#332C23")
        tokens.surfaces.border2 shouldBe hex("#3F4754")
        tokens.content.dim shouldBe hex("#717782")
    }

    @Test
    fun `dark theme keeps a muted that already passes and reuses bg and text for the terminal`() {
        val tokens = midnight()

        tokens.content.muted shouldBe hex("#9CA3AF")
        tokens.content.accentInk shouldBe Rgba.BLACK
        tokens.status.warnInk shouldBe Rgba.BLACK
        tokens.terminal.termBg shouldBe hex("#0B0F19")
        tokens.terminal.termText shouldBe hex("#F3F4F6")
        listOf(tokens.status.warn, tokens.status.ok, tokens.status.err) shouldBe
            listOf(hex("#F59E0B"), hex("#4ADE80"), hex("#F87171"))
    }

    @Test
    fun `muted is corrected against the composited panel, sel and warnBg`() {
        // panel #808080 at 50 % over white is #BFBFBF; against the uncomposited #808080 the result would be #171717.
        // sel (#ADB4BB) and warnBg (#B1ACA3) are darker than the composited panel, so they decide: #414141, not #4E4E4E
        val tokens =
            deriveShapedTokens(
                ShapedRow(
                    "t",
                    "T",
                    ThemeMode.LIGHT,
                    "#FFFFFF #808080/50 #000000 #505050 #3182CE #000000/5",
                    "1 8 none",
                    "plex",
                ),
            )

        tokens.content.muted shouldBe hex("#414141")
    }

    @Test
    fun `dark high contrast mode uses the dark rules`() {
        val tokens =
            deriveShapedTokens(
                ShapedRow(
                    "t",
                    "T",
                    ThemeMode.DARK_HIGH_CONTRAST,
                    "#000000 #000000 #FFFFFF #FFFFFF #FE8019 #333333",
                    "1 0 none",
                    "plex",
                ),
            )

        tokens.surfaces.raised shouldBe hex("#0F0F0F")
        tokens.terminal.termBg shouldBe hex("#000000")
    }

    @Test
    fun `translucent bg is rejected`() {
        val row =
            ShapedRow(
                "t",
                "T",
                ThemeMode.LIGHT,
                "#F0F0F0/50 #FFFFFF #000000 #808080 #3182CE #000000/5",
                "1 8 none",
                "plex",
            )

        shouldThrow<IllegalArgumentException> { deriveShapedTokens(row) }
    }

    @Test
    fun `accent with no status triple fails the derivation`() {
        val row =
            ShapedRow(
                "t",
                "T",
                ThemeMode.LIGHT,
                "#F0F0F0 #FFFFFF #000000 #808080 #FF0000 #000000/5",
                "1 8 none",
                "plex",
            )

        shouldThrow<IllegalStateException> { deriveShapedTokens(row) }
    }
}
