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
    fun `has nine base schemes in docs 08 order`() {
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
            )
    }

    @Test
    fun `has eight base typographies in docs 08 order`() {
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
            )
    }

    @Test
    fun `light schemes are paper and sand`() {
        catalog.themes.filter { !it.mode.isDark }.map { it.id.value } shouldBe listOf("paper", "sand")
    }

    @Test
    fun `contrast is the only high contrast scheme`() {
        catalog.themes.filter { it.mode.isHighContrast }.map { it.id.value to it.mode } shouldBe
            listOf("contrast" to ThemeMode.DARK_HIGH_CONTRAST)
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
        catalog.themes.map { it.defaultTypography }.toSet() shouldBe setOf(null)
    }

    @Test
    fun `amber warn is turquoise`() {
        scheme("amber").colors.status.warn shouldBe rgb(0x5EEAD4)
    }

    @ParameterizedTest(name = "{0} {1} is {2}")
    @CsvSource(
        "paper, warn, 0xA16207",
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

    private fun typography(
        key: String,
        name: String,
        ui: String,
        terminal: String,
    ): TypographySpec = TypographySpec(TypographyId(key), name, ui, terminal)

    companion object {
        @JvmStatic
        fun schemeKeys(): List<String> =
            listOf("plex", "graphite", "paper", "phosphor", "synth", "amber", "sand", "rose", "contrast")
    }
}
