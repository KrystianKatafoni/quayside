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
    private fun theme(key: String): ThemeSpec = builtInCatalog().theme(SchemeId(key)).shouldNotBeNull()

    private fun source(key: String): ThemeSource = ShapedThemeTable.rows.first { it.key == key }

    private fun panelOnBg(source: ThemeSource): Rgba = hexAlpha(source.panel).over(hex(source.bg))

    /** The surfaces the screens draw status colours on as text (AM-803 audit): bg, the composited panel and sel. */
    private fun textSurfaces(spec: ThemeSpec): List<Rgba> {
        val surfaces = spec.colors.surfaces
        return listOf(surfaces.bg, surfaces.panel.over(surfaces.bg), surfaces.sel)
    }

    /** [colors] corrected the way the catalog does it for [spec]. */
    private fun corrected(
        spec: ThemeSpec,
        vararg colors: String,
    ): List<Rgba> = colors.map { hex(it).readableOn(textSurfaces(spec), spec.mode.isDark) }

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
        colors.content.accent shouldBe hex(CORRECTED_ACCENTS[source.key] ?: source.accent)
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
        "midnight-pro, #9CA3AF",
        "nord-dark, #D8DEE9",
    )
    fun `muted that already passes is kept`(
        key: String,
        muted: String,
    ) {
        theme(key).colors.content.muted shouldBe hex(muted)
    }

    @ParameterizedTest(name = "{0}: {1} becomes {2}")
    @CsvSource(
        "nordic-light, #718096, #5D697B",
        "cyber-light, #6B7280, #626976",
        "sand-light, #8A867A, #636058",
        "steel-light, #6B778C, #5D687A",
        "term-light, #808080, #686868",
        "oatmeal-light, #8C7E70, #71665B",
        "emerald-light, #059669, #047753",
        "dracula, #6272A4, #CED3E3",
        "monokai, #75715E, #A5A396",
        "gruvbox, #928374, #C7C0B8",
        "developer-gray, #768390, #ABB3BB",
        "deep-abyss, #64748B, #8E9AAA",
        "teal-light, #64748B, #5B6A7E",
        "cyberpunk, #FF00FF, #FF29FF",
        "minimal-black, #888888, #9F9F9F",
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
    fun `muted is the source muted corrected against bg, the composited panel, sel and warnBg`(source: ThemeSource) {
        val spec = theme(source.key)
        val surfaces = spec.colors.surfaces
        val expected =
            hex(source.muted).withContrast(
                listOf(hex(source.bg), panelOnBg(source), surfaces.sel, spec.colors.status.warnBg),
                4.5,
                if (spec.mode.isDark) Rgba.WHITE else Rgba.BLACK,
            )

        spec.colors.content.muted shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("statusRows")
    fun `status colours follow the conflict rule`(row: StatusRow) {
        val spec = theme(row.key)
        val status = spec.colors.status

        // The candidates the hue rule picks, then darkened or lightened where a screen would draw them below 4.5:1
        listOf(status.warn, status.ok, status.err) shouldBe corrected(spec, *row.expected.toTypedArray())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("defaultStatusKeys")
    fun `other themes take the first status candidates`(key: String) {
        val spec = theme(key)
        val candidates =
            if (spec.mode.isDark) {
                arrayOf("#F59E0B", "#4ADE80", "#F87171")
            } else {
                arrayOf("#A16207", "#15803D", "#B91C1C")
            }

        listOf(spec.colors.status.warn, spec.colors.status.ok, spec.colors.status.err) shouldBe
            corrected(spec, *candidates)
    }

    @Test
    fun `oatmeal-light work is darker than accent`() {
        val colors = theme("oatmeal-light").colors

        colors.status.work shouldBe hex("#A35942")
        colors.status.work shouldNotBe colors.content.accent
        Wcag.luminance(colors.status.work) shouldBeLessThan Wcag.luminance(colors.content.accent)
    }

    @Test
    fun `oatmeal-light accent is darkened until it is a 3 to 1 mark on its sel`() {
        val colors = theme("oatmeal-light").colors

        // The docs/08 accent #D97757 is 2.77:1 on sel; sel is mixed from the accent itself, so it settles on #CE7153
        colors.content.accent shouldBe hex("#CE7153")
        Wcag.luminance(colors.content.accent) shouldBeLessThan Wcag.luminance(hex("#D97757"))
        Wcag.contrast(colors.content.accent, colors.surfaces.sel) shouldBeGreaterThan 3.0
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("hc-light", "steel-light", "cyberpunk", "minimal-black", "deep-abyss")
    fun `work is the accent when it already has contrast 4_5 to 1`(key: String) {
        val colors = theme(key).colors

        colors.status.work shouldBe colors.content.accent
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("nordic-light", "term-light", "dracula", "gruvbox", "developer-gray")
    fun `work is the accent corrected to 4_5 to 1 on bg, panel and sel when it is weaker`(key: String) {
        val spec = theme(key)
        val accent = spec.colors.content.accent

        spec.colors.status.work shouldBe accent.readableOn(textSurfaces(spec), spec.mode.isDark)
        spec.colors.status.work shouldNotBe accent
    }

    @Test
    fun `solarized-dark uses base2 text and base1 muted lightened for sel`() {
        val colors = theme("solarized-dark").colors

        colors.content.text shouldBe hex("#EEE8D5")
        // base1 #93A1A1 is below 4.5:1 on sel, so it is lightened to #A4B0B0
        colors.content.muted shouldBe hex("#A4B0B0")
        Wcag.luminance(colors.content.muted) shouldBeGreaterThan Wcag.luminance(hex("#93A1A1"))
    }

    @Test
    fun `dracula keeps its panel and lightens muted`() {
        val colors = theme("dracula").colors
        val original = hex("#6272A4")

        colors.surfaces.panel shouldBe hex("#44475A")
        Wcag.luminance(colors.content.muted) shouldBeGreaterThan Wcag.luminance(original)
        colors.content.muted shouldBe
            original.withContrast(
                listOf(hex("#282A36"), hex("#44475A"), colors.surfaces.sel, colors.status.warnBg),
                4.5,
                Rgba.WHITE,
            )
    }

    @Test
    fun `terminal colours are bg and text in every theme`() {
        ShapedThemeTable.rows.forEach { row ->
            val spec = theme(row.key)
            val terminal = spec.colors.terminal

            terminal.termBg shouldBe spec.colors.surfaces.bg
            terminal.termText shouldBe spec.colors.content.text
            terminal.ansi shouldBe deriveTerminal(terminal.termBg, terminal.termText, spec.mode.isDark).ansi
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
        /** Accents the AM-803 audit darkened: the docs/08 accent is below 3:1 on bg, raised or sel. */
        private val CORRECTED_ACCENTS = mapOf("oatmeal-light" to "#CE7153")

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
