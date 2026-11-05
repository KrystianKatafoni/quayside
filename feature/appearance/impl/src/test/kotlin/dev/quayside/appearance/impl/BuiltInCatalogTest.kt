package dev.quayside.appearance.impl

import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.ThemeMode
import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.core.designtokens.Backdrop
import dev.quayside.core.designtokens.ContrastAudit
import dev.quayside.core.designtokens.SampleTokens.rgb
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

class BuiltInCatalogTest {
    private val catalog: ThemeCatalog get() = appearanceModule().catalog

    private fun scheme(key: String): ThemeSpec = catalog.theme(SchemeId(key)).shouldNotBeNull()

    @Test
    @Suppress("LongMethod")
    fun `has nine base schemes then twenty shaped themes in docs 08 order`() {
        catalog.themes.map { it.id.value to it.name } shouldBe
            listOf(
                "plex" to "Plex Violet",
                "graphite" to "Graphite",
                "paper" to "Paper Indigo",
                "phosphor" to "Phosphor",
                "synth" to "Synth",
                "amber" to "Amber CRT",
                "sand" to "Sand",
                "rose" to "Rose Night",
                "contrast" to "Wysoki kontrast",
                "nordic-light" to "Nordic Slate",
                "cyber-light" to "Cyber Ink Light",
                "sand-light" to "Minimal Sand",
                "hc-light" to "High-Contrast Accessibility",
                "steel-light" to "Steel & Cobalt",
                "teal-light" to "Teal & Obsidian",
                "term-light" to "Terminal Classic",
                "glass-light" to "Amethyst Glass",
                "oatmeal-light" to "Oatmeal & Terracotta",
                "emerald-light" to "Corporate Emerald",
                "midnight-pro" to "Midnight Pro",
                "dracula" to "Dracula",
                "monokai" to "Monokai",
                "gruvbox" to "Gruvbox Dark",
                "cyberpunk" to "Cyberpunk",
                "nord-dark" to "Nord Dark",
                "minimal-black" to "Minimal Black",
                "solarized-dark" to "Solarized Dark",
                "developer-gray" to "Developer Gray",
                "deep-abyss" to "Deep Abyss",
            )
    }

    @Test
    fun `has twenty two typographies in docs 08 order`() {
        catalog.typographies shouldBe
            listOf(
                typography("plex", "IBM Plex", "IBM Plex Sans", "IBM Plex Mono"),
                typography("geist", "Geist", "Geist", "Geist Mono"),
                typography("instrument", "Instrument", "Instrument Sans", "DM Mono"),
                typography("jetbrains", "JetBrains Mono", "JetBrains Mono", "JetBrains Mono"),
                typography("chakra", "Chakra", "Chakra Petch", "Fira Code"),
                typography("space", "Space", "Space Grotesk", "Space Mono"),
                typography("hyper", "Hyperlegible", "Atkinson Hyperlegible Next", "Atkinson Hyperlegible Mono"),
                typography("martian", "Martian Mono", "Martian Mono", "Martian Mono"),
                pair("jakarta-jb", "Plus Jakarta Sans", "JetBrains Mono"),
                pair("grotesk-fira", "Space Grotesk", "Fira Code"),
                pair("outfit-courier", "Outfit", "Courier Prime"),
                pair("outfit-dm", "Outfit", "DM Mono"),
                pair("inter-fira", "Inter", "Fira Code"),
                pair("inter-jb", "Inter", "JetBrains Mono"),
                pair("inter-roboto", "Inter", "Roboto Mono"),
                pair("dmsans-jb", "DM Sans", "JetBrains Mono"),
                pair("dmsans-courier", "DM Sans", "Courier Prime"),
                pair("grotesk-source", "Space Grotesk", "Source Code Pro"),
                typography("ptmono", "PT Mono", "PT Mono", "PT Mono"),
                typography("plexmono", "IBM Plex Mono", "IBM Plex Mono", "IBM Plex Mono"),
                typography("firacode", "Fira Code", "Fira Code", "Fira Code"),
                typography("system", "Systemowa", "SF Pro", "Menlo"),
            )
    }

    @Test
    fun `twelve themes are light and seventeen dark`() {
        catalog.themes.filter { !it.mode.isDark }.map { it.id.value } shouldBe
            listOf("paper", "sand") +
            listOf(
                "nordic-light",
                "cyber-light",
                "sand-light",
                "hc-light",
                "steel-light",
                "teal-light",
                "term-light",
                "glass-light",
                "oatmeal-light",
                "emerald-light",
            )
        catalog.themes.count { it.mode.isDark } shouldBe 17
    }

    @Test
    fun `contrast and hc-light are the high contrast themes`() {
        catalog.themes.filter { it.mode.isHighContrast }.map { it.id.value to it.mode } shouldBe
            listOf(
                "contrast" to ThemeMode.DARK_HIGH_CONTRAST,
                "hc-light" to ThemeMode.LIGHT_HIGH_CONTRAST,
            )
    }

    @ParameterizedTest(name = "{0} has radius {1}")
    @CsvSource(
        "plex, 8",
        "graphite, 6",
        "paper, 10",
        "phosphor, 0",
        "synth, 4",
        "amber, 4",
        "sand, 8",
        "rose, 10",
        "contrast, 2",
    )
    fun `base shapes have table radius, border 1, no shadow, opaque panel`(
        key: String,
        radius: Int,
    ) {
        val theme = scheme(key)

        theme.shape.radius shouldBe radius
        theme.shape.borderWidth shouldBe 1
        theme.shape.shadow.shouldBeNull()
        theme.shape.backdrop shouldBe Backdrop.OPAQUE
        theme.colors.surfaces.panel.isOpaque shouldBe true
    }

    @Test
    fun `base schemes have no default typography`() {
        catalog.themes
            .take(BASE_SCHEMES)
            .map { it.defaultTypography }
            .toSet() shouldBe setOf(null)
        catalog.themes.drop(BASE_SCHEMES).all { it.defaultTypography != null } shouldBe true
    }

    @Test
    fun `amber warn is turquoise`() {
        scheme("amber").colors.status.warn shouldBe rgb(0x5EEAD4)
    }

    @ParameterizedTest(name = "{0} {1} is {2}")
    @CsvSource(
        // AM-803 audit: the docs/08 warn #A16207 is below 4.5:1 on paper's sel, so it is darkened to #965B07
        "paper, warn, 0x965B07",
        "phosphor, ok, 0xA3E635",
        "synth, ok, 0x4ADE80",
        "rose, accent, 0xC4A7E7",
    )
    fun `colours corrected by the AM-801 audit are used`(
        key: String,
        token: String,
        expected: String,
    ) {
        val colors = scheme(key).colors
        val actual =
            when (token) {
                "warn" -> colors.status.warn
                "ok" -> colors.status.ok
                "accent" -> colors.content.accent
                else -> error("unknown token $token")
            }

        actual shouldBe rgb(java.lang.Long.decode(expected))
    }

    @ParameterizedTest(name = "{0} meets NFR-6 contrast and hue rules")
    @MethodSource("schemeKeys")
    fun `scheme meets NFR-6 contrast and hue rules`(key: String) {
        val problems = ContrastAudit.problems(scheme(key).colors)

        withClue("$key: ${problems.joinToString("; ")}") { problems.shouldBeEmpty() }
    }

    private fun pair(
        key: String,
        ui: String,
        terminal: String,
    ): TypographySpec = typography(key, "$ui + $terminal", ui, terminal)

    private fun typography(
        key: String,
        name: String,
        ui: String,
        terminal: String,
    ): TypographySpec = TypographySpec(TypographyId(key), name, ui, terminal)

    companion object {
        private const val BASE_SCHEMES = 9

        @JvmStatic
        fun schemeKeys(): List<String> =
            listOf("plex", "graphite", "paper", "phosphor", "synth", "amber", "sand", "rose", "contrast") +
                ShapedThemesTest.KEYS
    }
}
