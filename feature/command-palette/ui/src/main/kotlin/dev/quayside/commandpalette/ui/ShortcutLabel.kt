package dev.quayside.commandpalette.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import dev.quayside.commandpalette.api.ChordKey
import dev.quayside.commandpalette.api.KeyChord
import dev.quayside.commandpalette.api.KeyModifier
import dev.quayside.commandpalette.api.Shortcut

/** The text of a shortcut in a palette row: macOS modifier order (control, option, shift, command). */
internal object ShortcutLabel {
    private val modifierSymbols =
        listOf(
            KeyModifier.CTRL to "⌃",
            KeyModifier.ALT to "⌥",
            KeyModifier.SHIFT to "⇧",
            KeyModifier.META to "⌘",
        )

    private val namedSymbols =
        mapOf(
            ChordKey.Named.ENTER to "⏎",
            ChordKey.Named.ESCAPE to "⎋",
            ChordKey.Named.BACKSPACE to "⌫",
            ChordKey.Named.UP to "↑",
            ChordKey.Named.DOWN to "↓",
            ChordKey.Named.SPACE to "␣",
        )

    fun of(shortcut: Shortcut): String = shortcut.chords.joinToString(separator = " ", transform = ::chord)

    private fun chord(chord: KeyChord): String {
        val modifiers = modifierSymbols.filter { it.first in chord.modifiers }.joinToString("") { it.second }
        return modifiers + key(chord.key, upper = modifiers.isNotEmpty())
    }

    private fun key(
        key: ChordKey,
        upper: Boolean,
    ): String =
        when (key) {
            is ChordKey.Letter -> if (upper) key.char.uppercase() else key.char.toString()
            is ChordKey.Named -> namedSymbols.getValue(key)
        }
}

/**
 * The Compose menu binding of this shortcut: only one chord with ⌘; null otherwise (shown in the palette only).
 * Letters and digits use the AWT key codes, which equal their upper-case character codes.
 */
public fun Shortcut.toKeyShortcut(): KeyShortcut? {
    val chord = chords.singleOrNull()?.takeIf { KeyModifier.META in it.modifiers } ?: return null
    val key =
        when (val chordKey = chord.key) {
            is ChordKey.Letter -> Key(chordKey.char.uppercaseChar().code)
            is ChordKey.Named -> KeyChords.named.getValue(chordKey)
        }
    return KeyShortcut(
        key = key,
        ctrl = KeyModifier.CTRL in chord.modifiers,
        meta = true,
        alt = KeyModifier.ALT in chord.modifiers,
        shift = KeyModifier.SHIFT in chord.modifiers,
    )
}
