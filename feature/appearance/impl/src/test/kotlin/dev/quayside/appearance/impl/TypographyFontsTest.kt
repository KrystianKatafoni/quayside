package dev.quayside.appearance.impl

import dev.quayside.core.designsystem.AmFonts
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Every typography of the catalog can be rendered: its fonts are bundled, or it uses a macOS system font. */
class TypographyFontsTest {
    private val typographies = builtInCatalog().typographies

    @Test
    fun `every catalog typography has bundled fonts or is a macOS system font`() {
        val unresolved =
            typographies.flatMap { spec ->
                listOf(spec.uiFont, spec.terminalFont)
                    .filter { it !in AmFonts.families && it !in SYSTEM_FONTS }
                    .map { "${spec.id.value}: $it" }
            }

        unresolved shouldBe emptyList()
    }

    @Test
    fun `system fonts are not bundled`() {
        SYSTEM_FONTS.forEach { AmFonts.family(it) shouldBe null }
        SYSTEM_FONTS.forEach { (it in AmFonts.families) shouldBe false }
    }

    @Test
    fun `only the system typography uses system fonts`() {
        val users =
            typographies
                .filter { it.uiFont in SYSTEM_FONTS || it.terminalFont in SYSTEM_FONTS }
                .map { it.id.value }

        users shouldBe listOf("system")
        typographies.firstOrNull { it.id.value == "system" }.shouldNotBeNull().let {
            it.uiFont shouldBe "SF Pro"
            it.terminalFont shouldBe "Menlo"
        }
    }

    @Test
    fun `bundled families are all used by some typography`() {
        val used = typographies.flatMap { listOf(it.uiFont, it.terminalFont) }.toSet()

        (AmFonts.families - used) shouldBe emptySet()
    }

    private companion object {
        val SYSTEM_FONTS = setOf("SF Pro", "Menlo")
    }
}
