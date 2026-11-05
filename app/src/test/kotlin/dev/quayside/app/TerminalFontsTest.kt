package dev.quayside.app

import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.Font

/**
 * The bundled terminal font goes to AWT so JediTerm can find it by name (AM-807). A recording lambda stands in for
 * `GraphicsEnvironment`, so the result does not depend on the fonts installed on the machine.
 */
class TerminalFontsTest {
    private val logger = RecordingLogger()
    private val registered = mutableListOf<Font>()

    @Test
    fun `plex terminal font registers both IBM Plex Mono files`() {
        val count =
            registerTerminalFont("IBM Plex Mono", logger) {
                registered += it
                true
            }

        count shouldBe 2
        registered shouldHaveSize 2
        registered.map { it.family }.toSet() shouldBe setOf("IBM Plex Mono", "IBM Plex Mono SemiBold")
    }

    @Test
    fun `system font registers nothing`() {
        val count =
            registerTerminalFont("Menlo", logger) {
                registered += it
                true
            }

        count shouldBe 0
        registered shouldHaveSize 0
    }

    @Test
    fun `unknown family registers nothing`() {
        val count =
            registerTerminalFont("No Such Family", logger) {
                registered += it
                true
            }

        count shouldBe 0
        registered shouldHaveSize 0
    }

    @Test
    fun `rejected font is not counted`() {
        val count =
            registerTerminalFont("IBM Plex Mono", logger) {
                registered += it
                false
            }

        count shouldBe 0
        registered shouldHaveSize 2
    }
}
