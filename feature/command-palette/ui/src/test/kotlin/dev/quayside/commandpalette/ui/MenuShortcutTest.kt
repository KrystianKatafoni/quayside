package dev.quayside.commandpalette.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import dev.quayside.commandpalette.api.ChordKey
import dev.quayside.commandpalette.api.KeyChord
import dev.quayside.commandpalette.api.KeyModifier
import dev.quayside.commandpalette.api.Shortcut
import dev.quayside.commandpalette.api.letterChord
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Mapping of the Compose-free [Shortcut] to the `MenuBar` binding (ADR 0011, decision 2). */
class MenuShortcutTest {
    @Test
    fun `cmd shift O maps to Key O with meta and shift`() {
        Shortcut.of(letterChord('o', KeyModifier.META, KeyModifier.SHIFT)).toKeyShortcut() shouldBe
            KeyShortcut(Key.O, meta = true, shift = true)
    }

    @Test
    fun `cmd backspace maps to Backspace`() {
        Shortcut.of(KeyChord(ChordKey.Named.BACKSPACE, setOf(KeyModifier.META))).toKeyShortcut() shouldBe
            KeyShortcut(Key.Backspace, meta = true)
    }

    @Test
    fun `cmd K maps to Key K`() {
        Shortcut.of(letterChord('k', KeyModifier.META)).toKeyShortcut() shouldBe
            KeyShortcut(Key.K, meta = true)
    }

    @Test
    fun `ctrl and alt are passed on`() {
        val shortcut = Shortcut.of(letterChord('j', KeyModifier.META, KeyModifier.CTRL, KeyModifier.ALT))

        shortcut.toKeyShortcut() shouldBe KeyShortcut(Key.J, ctrl = true, meta = true, alt = true)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(chars = ['a', 'o', 'z', '0', '9'])
    fun `letters and digits map to their Compose keys`(char: Char) {
        Shortcut.of(letterChord(char, KeyModifier.META)).toKeyShortcut() shouldBe
            KeyShortcut(KEYS.getValue(char), meta = true)
    }

    @Test
    fun `chord without meta has no menu shortcut`() {
        Shortcut.of(letterChord('g')).toKeyShortcut().shouldBeNull()
        Shortcut.of(letterChord('k', KeyModifier.SHIFT)).toKeyShortcut().shouldBeNull()
    }

    @Test
    fun `two chords have no menu shortcut`() {
        Shortcut.of(letterChord('g'), letterChord('p')).toKeyShortcut().shouldBeNull()
        Shortcut
            .of(letterChord('k', KeyModifier.META), letterChord('p', KeyModifier.META))
            .toKeyShortcut()
            .shouldBeNull()
    }

    private companion object {
        val KEYS: Map<Char, Key> = mapOf('a' to Key.A, 'o' to Key.O, 'z' to Key.Z, '0' to Key.Zero, '9' to Key.Nine)
    }
}
