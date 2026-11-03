package dev.quayside.appearance.impl

import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.core.designtokens.Backdrop
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.ShadowSpec
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.over
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** The 20 shaped themes of the catalog against the docs/08 table (AM-805). */
class ShapedThemesTest {
    private fun theme(key: String): ThemeSpec = appearanceModule().catalog.theme(SchemeId(key)).shouldNotBeNull()

    private fun source(key: String): ThemeSource = ShapedThemeTable.rows.first { it.key == key }

    private fun panelOnBg(source: ThemeSource): Rgba = hexAlpha(source.panel).over(hex(source.bg))

    private fun shadowOf(text: String): ShadowSpec? {
        if (text == "none") return null
        val parts = text.split('/')
        return ShadowSpec(
            offsetY = parts[0].toInt(),
            blur = parts[1].toInt(),
            spread = parts[2].toInt(),
            color = hex(parts[3]).withAlpha(parts[4].toInt()),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    fun `shaped themes have table border, radius and shadow`(source: ThemeSource) {
        val shape = theme(source.key).shape

        shape.borderWidth shouldBe source.borderWidth
        shape.radius shouldBe source.radius
        shape.shadow shouldBe shadowOf(source.shadow)
    }

    @Test
    fun `only glass-light has a translucent panel`() {
        ShapedThemeTable.rows.forEach { row ->
            val spec = theme(row.key)
            val translucent = row.key == "glass-light"

            spec.shape.backdrop shouldBe if (translucent) Backdrop.TRANSLUCENT else Backdrop.OPAQUE
            spec.colors.surfaces.panel.isOpaque shouldBe !translucent
        }
        val glass = theme("glass-light").colors.surfaces

        glass.panel.alpha shouldBe 0x99
        glass.panel.over(glass.bg) shouldBe hex("#FAF9FF")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    fun `shaped themes suggest the docs 08 typography`(source: ThemeSource) {
        theme(source.key).defaultTypography shouldBe TypographyId(source.typography)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    fun `source colours bg, panel, accent and border are kept`(source: ThemeSource) {
        val colors = theme(source.key).colors

        colors.surfaces.bg shouldBe hex(source.bg)
        colors.surfaces.panel shouldBe hexAlpha(source.panel)
        colors.content.accent shouldBe hex(source.accent)
        colors.surfaces.border shouldBe hexAlpha(source.border)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    fun `source text is kept`(source: ThemeSource) {
        theme(source.key).colors.content.text shouldBe hex(source.text)
    }

    @ParameterizedTest(name = "{0} keeps {1}")
    @CsvSource(
        "hc-light, #4A4A4A",
        "teal-light, #64748B",
        "midnight-pro, #9CA3AF",
        "cyberpunk, #FF00FF",
        "nord-dark, #D8DEE9",
        "minimal-black, #888888",
    )
    fun `muted that already passes is kept`(
        key: String,
        muted: String,
    ) {
        theme(key).colors.content.muted shouldBe hex(muted)
    }

    @ParameterizedTest(name = "{0}: {1} becomes {2}")
    @CsvSource(
        "nordic-light, #718096, #657286",
        "cyber-light, #6B7280, #69707D",
        "sand-light, #8A867A, #716E64",
        "steel-light, #6B778C, #616C7F",
        "term-light, #808080, #6D6D6D",
        "oatmeal-light, #8C7E70, #7B6F63",
        "emerald-light, #059669, #04835B",
        "dracula, #6272A4, #ADB6D0",
        "monokai, #75715E, #928F80",
        "gruvbox, #928374, #ABA094",
        "developer-gray, #768390, #909BA5",
        "deep-abyss, #64748B, #707F94",
    )
    fun `corrected muted moves away from the background`(
        key: String,
        original: String,
        corrected: String,
    ) {
        val spec = theme(key)
        val muted = spec.colors.content.muted

        muted shouldBe hex(corrected)
        if (spec.mode.isDark) {
            Wcag.luminance(muted) shouldBeGreaterThan Wcag.luminance(hex(original))
        } else {
            Wcag.luminance(muted) shouldBeLessThan Wcag.luminance(hex(original))
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sources")
    fun `muted is the source muted corrected against bg and the composited panel`(source: ThemeSource) {
        val dark = theme(source.key).mode.isDark
        val expected =
            hex(source.muted).withContrast(
                listOf(hex(source.bg), panelOnBg(source)),
                4.5,
                if (dark) Rgba.WHITE else Rgba.BLACK,
            )

        theme(source.key).colors.content.muted shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("statusRows")
    fun `status colours follow the conflict rule`(row: StatusRow) {
        val status = theme(row.key).colors.status

        listOf(status.warn, status.ok, status.err) shouldBe row.expected.map(::hex)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("defaultStatusKeys")
    fun `other themes take the first status candidates`(key: String) {
        val spec = theme(key)
        val expected =
            if (spec.mode.isDark) {
                listOf("#F59E0B", "#4ADE80", "#F87171")
            } else {
                listOf("#A16207", "#15803D", "#B91C1C")
            }

        listOf(spec.colors.status.warn, spec.colors.status.ok, spec.colors.status.err) shouldBe expected.map(::hex)
    }

    @Test
    fun `oatmeal-light work is darker than accent`() {
        val colors = theme("oatmeal-light").colors

        colors.status.work shouldBe hex("#D27354")
        colors.status.work shouldNotBe colors.content.accent
        Wcag.luminance(colors.status.work) shouldBeLessThan Wcag.luminance(colors.content.accent)
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("nordic-light", "term-light", "dracula", "gruvbox", "minimal-black", "developer-gray")
    fun `work is the accent when it already has contrast 3 to 1`(key: String) {
        val colors = theme(key).colors

        colors.status.work shouldBe colors.content.accent
    }

    @Test
    fun `solarized-dark uses base2 text and base1 muted`() {
        val colors = theme("solarized-dark").colors

        colors.content.text shouldBe hex("#EEE8D5")
        colors.content.muted shouldBe hex("#93A1A1")
    }

    @Test
    fun `dracula keeps its panel and lightens muted`() {
        val colors = theme("dracula").colors
        val original = hex("#6272A4")

        colors.surfaces.panel shouldBe hex("#44475A")
        Wcag.luminance(colors.content.muted) shouldBeGreaterThan Wcag.luminance(original)
        colors.content.muted shouldBe
            original.withContrast(listOf(hex("#282A36"), hex("#44475A")), 4.5, Rgba.WHITE)
    }

    @Test
    fun `terminal colours are bg and text for dark themes and a fixed pair for light ones`() {
        ShapedThemeTable.rows.forEach { row ->
            val spec = theme(row.key)
            val terminal = spec.colors.terminal

            if (spec.mode.isDark) {
                terminal.termBg shouldBe spec.colors.surfaces.bg
                terminal.termText shouldBe spec.colors.content.text
            } else {
                terminal.termBg shouldBe hex("#1E242C")
                terminal.termText shouldBe hex("#E2E8F0")
            }
        }
    }

    /** Status triple expected for one theme (warn, ok, err). */
    data class StatusRow(
        val key: String,
        val expected: List<String>,
    ) {
        override fun toString(): String = key
    }

    companion object {
        val KEYS: List<String> = ShapedThemeTable.rows.map { it.key }

        @JvmStatic
        fun sources(): Stream<ThemeSource> = ShapedThemeTable.rows.stream()

        @JvmStatic
        fun statusRows(): Stream<StatusRow> =
            Stream.of(
                StatusRow("term-light", listOf("#A16207", "#4D7C0F", "#B91C1C")),
                StatusRow("emerald-light", listOf("#A16207", "#4D7C0F", "#B91C1C")),
                StatusRow("oatmeal-light", listOf("#0F766E", "#15803D", "#BE185D")),
                StatusRow("monokai", listOf("#FACC15", "#4ADE80", "#FF6E40")),
                StatusRow("gruvbox", listOf("#5EEAD4", "#A3E635", "#FB7185")),
                StatusRow("cyberpunk", listOf("#F59E0B", "#A3E635", "#F87171")),
                StatusRow("deep-abyss", listOf("#5EEAD4", "#A3E635", "#F87171")),
            )

        @JvmStatic
        fun defaultStatusKeys(): Stream<String> =
            Stream.of(
                "nordic-light",
                "cyber-light",
                "sand-light",
                "hc-light",
                "steel-light",
                "teal-light",
                "glass-light",
                "midnight-pro",
                "dracula",
                "nord-dark",
                "minimal-black",
                "solarized-dark",
                "developer-gray",
            )
    }
}
