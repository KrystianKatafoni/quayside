package dev.agentmanager.commandpalette.ui

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test

/** Texts of `i18n/palette.properties` (NFR-10). */
class PaletteStringsTest {
    @Test
    fun `every key has a text`() {
        PaletteStrings.keys shouldContainAll KEYS
        KEYS.forEach { key -> withClue(key) { PaletteStrings[key].shouldNotBeBlank() } }
    }

    @Test
    fun `no match text says what the plan says`() {
        PaletteStrings["palette.empty"] shouldBe "Brak pasujących komend"
    }

    private companion object {
        val KEYS = setOf("palette.placeholder", "palette.empty")
    }
}
