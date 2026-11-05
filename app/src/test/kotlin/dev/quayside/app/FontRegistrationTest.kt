package dev.quayside.app

import dev.quayside.appearance.impl.appearanceModule
import dev.quayside.core.designsystem.AmFonts
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.Font

/**
 * The terminal fonts of the catalog go to AWT once, before the first window (AM-807, AM-803): a
 * recording lambda stands in for `GraphicsEnvironment`.
 */
class FontRegistrationTest {
    private val catalog = appearanceModule().catalog
    private val logger = RecordingLogger()
    private val registered = mutableListOf<Font>()

    private fun families(): List<String> = registered.map { it.family }

    private fun record(font: Font): Boolean {
        registered += font
        return true
    }

    /** How many files of [font] reach AWT; 0 for a system font. */
    private fun filesOf(font: String): Int = registerTerminalFont(font, logger) { true }

    @Test
    fun `every terminal font the dialog offers is registered`() {
        // The choices of the dialog: the terminal font of every typography, once each (terminalFontChoices)
        val offered = catalog.typographies.map { it.terminalFont }.distinct()
        val bundled = offered.filter { it in AmFonts.families }

        registerTerminalFonts(catalog, logger, ::record)

        bundled.forEach { (filesOf(it) > 0) shouldBe true }
        registered.size shouldBe offered.sumOf(::filesOf)
    }

    @Test
    fun `a font shared by two typographies is registered once`() {
        val shared = catalog.typographies.first().copy(id = TypographyId("again"))

        registerTerminalFonts(catalog.copy(typographies = catalog.typographies + shared), logger, ::record)

        registered.size shouldBe
            catalog.typographies
                .map { it.terminalFont }
                .distinct()
                .sumOf(::filesOf)
    }

    @Test
    fun `a system font registers nothing`() {
        registerTerminalFont("Menlo", logger, ::record)

        registered shouldBe emptyList()
    }

    @Test
    fun `every catalog typography has bundled or system fonts`() {
        // Every terminal font of the catalog is registered from the bundle, or it is a macOS system font
        val system = setOf("Menlo", "SF Pro")
        catalog.typographies.forEach { spec ->
            val count = registerTerminalFont(spec.terminalFont, logger) { true }
            if (spec.terminalFont in system) {
                count shouldBe 0
            } else {
                (spec.terminalFont in AmFonts.families) shouldBe true
                (count > 0) shouldBe true
            }
            (spec.uiFont in AmFonts.families || spec.uiFont in system) shouldBe true
        }
    }
}
