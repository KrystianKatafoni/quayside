package dev.quayside.commandpalette.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import dev.quayside.commandpalette.api.ChordKey
import dev.quayside.commandpalette.api.KeyChord
import dev.quayside.commandpalette.api.KeyModifier

/** Maps Compose keys to [KeyChord]s (AM-409); the inverse of `toKeyShortcut`. */
internal object KeyChords {
    /** Named chord keys and their Compose keys, shared with `toKeyShortcut`. */
    val named =
        mapOf(
            ChordKey.Named.ENTER to Key.Enter,
            ChordKey.Named.ESCAPE to Key.Escape,
            ChordKey.Named.SPACE to Key.Spacebar,
            ChordKey.Named.BACKSPACE to Key.Backspace,
            ChordKey.Named.UP to Key.DirectionUp,
            ChordKey.Named.DOWN to Key.DirectionDown,
        )

    /** The chord of [key] with [modifiers]; null for modifier keys and unknown keys. */
    fun of(
        key: Key,
        modifiers: Set<KeyModifier>,
    ): KeyChord? {
        val char = key.nativeKeyCode.toChar()
        val chordKey: ChordKey? =
            named.entries.firstOrNull { it.value == key }?.key
                ?: if (char in 'A'..'Z' || char in '0'..'9') ChordKey.Letter(char.lowercaseChar()) else null
        return chordKey?.let { KeyChord(it, modifiers) }
    }
}

/** The chord of a key-down event; null for other event types and for keys without a chord. */
internal fun KeyEvent.toChord(): KeyChord? {
    if (type != KeyEventType.KeyDown) return null
    val modifiers =
        buildSet {
            if (isMetaPressed) add(KeyModifier.META)
            if (isShiftPressed) add(KeyModifier.SHIFT)
            if (isAltPressed) add(KeyModifier.ALT)
            if (isCtrlPressed) add(KeyModifier.CTRL)
        }
    return KeyChords.of(key, modifiers)
}
