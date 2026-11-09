package dev.quayside.appearance.impl

import dev.quayside.appearance.api.SampleSchemes
import dev.quayside.appearance.api.SchemeError
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.ThemeMode
import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.core.designtokens.Backdrop
import dev.quayside.core.designtokens.ContrastAudit
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.domain.Outcome
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class CustomSchemesTest {
    private val mine = SampleSchemes.mine()
    private val base = builtInCatalog()

    @Test
    fun `hex is upper case with six digits`() {
        Rgba(0xFF0A0B0CL).hex() shouldBe "#0A0B0C"
        Rgba(0xFFFFFFFFL).hex() shouldBe "#FFFFFF"
    }

    @Test
    fun `theme of a scheme copies its identity, radius and a flat shape`() {
        val spec = themeOf(mine.copy(mode = ThemeMode.LIGHT, radius = 12))

        spec.id shouldBe mine.id
        spec.name shouldBe mine.name
        spec.mode shouldBe ThemeMode.LIGHT
        spec.shape.radius shouldBe 12
        spec.shape.borderWidth shouldBe 1
        spec.shape.shadow shouldBe null
        spec.shape.backdrop shouldBe Backdrop.OPAQUE
        spec.defaultTypography shouldBe null
    }

    @Test
    fun `theme of graphite colours equals the built-in graphite tokens`() {
        val graphite = checkNotNull(base.theme(SchemeId("graphite")))

        themeOf(mine).colors shouldBe graphite.colors
    }

    @Test
    fun `audited theme of a readable scheme is Ok and passes the audit`() {
        auditedThemeOf(mine) shouldBe Outcome.Ok(themeOf(mine))
        ContrastAudit.problems(themeOf(mine).colors).shouldBeEmpty()
    }

    @Test
    fun `unreadable text is LowContrast with the violated rules`() {
        val grey = mine.copy(colors = mine.colors.copy(text = SampleSchemes.color("#222222")))

        val error = auditedThemeOf(grey).shouldBeInstanceOf<Outcome.Err<SchemeError>>().error

        error.shouldBeInstanceOf<SchemeError.LowContrast>().problems.shouldNotBeEmpty()
    }

    @Test
    fun `accepted schemes drop taken ids, repeats and unreadable ones in order`() {
        val grey = mine.copy(id = SchemeId("grey"), colors = mine.colors.copy(text = SampleSchemes.color("#222222")))
        val other = mine.copy(id = SchemeId("other"))
        val builtIn = mine.copy(id = ThemeCatalog.DEFAULT_SCHEME)

        acceptedSchemes(base, listOf(builtIn, grey, mine, mine.copy(name = "Again"), other)) shouldBe
            listOf(mine, other)
    }

    @Test
    fun `catalog with schemes appends them and no schemes keeps the base instance`() {
        catalogWith(base, emptyList()) shouldBe base

        val extended = catalogWith(base, listOf(mine))

        extended.themes shouldBe base.themes + themeOf(mine)
        extended.typographies shouldBe base.typographies
    }

    @Test
    fun `custom scheme gets the terminal colors derived by the AM-811 rule`() {
        val light = mine.copy(mode = ThemeMode.LIGHT, colors = lightColors())

        val terminal = themeOf(light).colors.terminal

        terminal shouldBe deriveTerminal(light.colors.bg, light.colors.text, dark = false)
        terminal.termBg shouldBe light.colors.bg
        terminal.termText shouldBe light.colors.text
    }

    @Test
    fun `terminal palette of a readable light custom scheme passes the audit`() {
        val light = mine.copy(mode = ThemeMode.LIGHT, colors = lightColors())

        auditedThemeOf(light).shouldBeInstanceOf<Outcome.Ok<ThemeSpec>>()
    }

    private fun lightColors() =
        mine.colors.copy(
            bg = SampleSchemes.color("#F4F6F8"),
            panel = SampleSchemes.color("#FFFFFF"),
            text = SampleSchemes.color("#2D3748"),
        )
}
