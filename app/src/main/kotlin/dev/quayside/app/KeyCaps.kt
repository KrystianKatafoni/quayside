package dev.quayside.app

import dev.quayside.commandpalette.api.ChordKey
import dev.quayside.commandpalette.api.KeyChord
import dev.quayside.commandpalette.api.KeyModifier
import dev.quayside.commandpalette.api.Shortcut
import dev.quayside.missioncontrol.ui.SessionKeyCaps

private val MODIFIER_SYMBOLS =
    listOf(KeyModifier.CTRL to "⌃", KeyModifier.ALT to "⌥", KeyModifier.SHIFT to "⇧", KeyModifier.META to "⌘")

private val NAMED_SYMBOLS =
    mapOf(
        ChordKey.Named.ENTER to "⏎",
        ChordKey.Named.ESCAPE to "⎋",
        ChordKey.Named.BACKSPACE to "⌫",
        ChordKey.Named.UP to "↑",
        ChordKey.Named.DOWN to "↓",
        ChordKey.Named.SPACE to "␣",
    )

/**
 * The text of [shortcut] next to an action, e.g. `⏎`, `r`, `⌘N`, `⇧⌘O` (macOS order of modifiers: control, option,
 * shift, command; a letter with a modifier is capital). A copy of `ShortcutLabel` of `command-palette:ui`, which is
 * `internal` there (plan AM-809, question 3).
 */
internal fun keyCapOf(shortcut: Shortcut): String =
    shortcut.chords.joinToString(separator = " ", transform = ::chordCap)

private fun chordCap(chord: KeyChord): String {
    val modifiers = MODIFIER_SYMBOLS.filter { it.first in chord.modifiers }.joinToString(separator = "") { it.second }
    val key =
        when (val key = chord.key) {
            is ChordKey.Letter -> if (modifiers.isEmpty()) key.char.toString() else key.char.uppercase()
            is ChordKey.Named -> NAMED_SYMBOLS.getValue(key)
        }
    return modifiers + key
}

/** The key caps of the window shortcuts for Mission Control (attach, reply, stop, new agent). */
internal fun windowKeyCaps(): SessionKeyCaps =
    SessionKeyCaps(
        attach = keyCapOf(AppShortcuts.ATTACH_SESSION),
        reply = keyCapOf(AppShortcuts.REPLY_SESSION),
        stop = keyCapOf(AppShortcuts.STOP_SESSION),
        newAgent = keyCapOf(AppShortcuts.NEW_AGENT),
    )
