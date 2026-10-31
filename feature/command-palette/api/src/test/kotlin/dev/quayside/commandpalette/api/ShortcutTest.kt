package dev.quayside.commandpalette.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ShortcutTest {
    private val g = letterChord('g')
    private val p = letterChord('p')

    @ParameterizedTest(name = "{0} chords -> accepted: {1}")
    @CsvSource("0, false", "1, true", "2, true", "3, false")
    fun `shortcut needs one or two chords`(
        count: Int,
        accepted: Boolean,
    ) {
        val chords = List(count) { g }
        if (accepted) {
            Shortcut(chords).chords shouldBe chords
        } else {
            shouldThrow<IllegalArgumentException> { Shortcut(chords) }
        }
    }

    @Test
    fun `of builds the same shortcut as the constructor`() {
        Shortcut.of(g, p) shouldBe Shortcut(listOf(g, p))
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(chars = ['a', 'g', 'z', '0', '5', '9'])
    fun `letter accepts only a to z and digits - accepted`(char: Char) {
        ChordKey.Letter(char).char shouldBe char
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(chars = ['A', 'Z', ' ', '-', '/', 'ł', 'é', '!'])
    fun `letter accepts only a to z and digits - rejected`(char: Char) {
        shouldThrow<IllegalArgumentException> { ChordKey.Letter(char) }
    }
}
