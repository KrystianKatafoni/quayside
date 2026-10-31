package dev.agentmanager.commandpalette.ui

import dev.agentmanager.commandpalette.api.ChordKey
import dev.agentmanager.commandpalette.api.KeyChord
import dev.agentmanager.commandpalette.api.KeyModifier
import dev.agentmanager.commandpalette.api.Shortcut
import dev.agentmanager.commandpalette.api.letterChord
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** The text of a shortcut in a palette row (macOS order of modifiers: control, option, shift, command). */
class ShortcutLabelTest {
    @Test
    fun `meta and shift come in mac order`() {
        ShortcutLabel.of(Shortcut.of(letterChord('o', KeyModifier.META, KeyModifier.SHIFT))) shouldBe "⇧⌘O"
        ShortcutLabel.of(
            Shortcut.of(letterChord('k', KeyModifier.META, KeyModifier.ALT, KeyModifier.CTRL, KeyModifier.SHIFT)),
        ) shouldBe "⌃⌥⇧⌘K"
        ShortcutLabel.of(Shortcut.of(letterChord('k', KeyModifier.META))) shouldBe "⌘K"
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource("ENTER,⏎", "ESCAPE,⎋", "BACKSPACE,⌫", "UP,↑", "DOWN,↓", "SPACE,␣")
    fun `named keys use symbols`(
        key: ChordKey.Named,
        symbol: String,
    ) {
        ShortcutLabel.of(Shortcut.of(KeyChord(key))) shouldBe symbol
    }

    @Test
    fun `named key with a modifier`() {
        ShortcutLabel.of(Shortcut.of(KeyChord(ChordKey.Named.BACKSPACE, setOf(KeyModifier.META)))) shouldBe "⌘⌫"
    }

    @Test
    fun `two chords are joined by a space`() {
        ShortcutLabel.of(Shortcut.of(letterChord('g'), letterChord('p'))) shouldBe "g p"
    }
}
