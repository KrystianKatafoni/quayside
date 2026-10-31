package dev.agentmanager.app

import dev.agentmanager.commandpalette.api.ChordKey
import dev.agentmanager.commandpalette.api.CommandRegistry
import dev.agentmanager.commandpalette.api.KeyChord
import dev.agentmanager.commandpalette.api.KeyModifier
import dev.agentmanager.commandpalette.api.Shortcut
import dev.agentmanager.commandpalette.ui.CommandPaletteController
import dev.agentmanager.terminal.ui.TerminalKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.event.KeyEvent

private val NAMED_KEYS =
    mapOf(
        KeyEvent.VK_ENTER to ChordKey.Named.ENTER,
        KeyEvent.VK_ESCAPE to ChordKey.Named.ESCAPE,
        KeyEvent.VK_SPACE to ChordKey.Named.SPACE,
        KeyEvent.VK_BACK_SPACE to ChordKey.Named.BACKSPACE,
        KeyEvent.VK_UP to ChordKey.Named.UP,
        KeyEvent.VK_DOWN to ChordKey.Named.DOWN,
    )

/** Keys with ⌘ that edit or move text in the terminal; they never reach app shortcuts (⌘⌫ removes a project). */
private val TEXT_EDITING: Set<ChordKey> = setOf(ChordKey.Named.BACKSPACE, ChordKey.Named.UP, ChordKey.Named.DOWN)

/** The chord of a key pressed in a terminal; null for keys without a chord and for lone modifier keys. */
internal fun chordOf(key: TerminalKey): KeyChord? {
    val modifiers =
        buildSet {
            if (key.meta) add(KeyModifier.META)
            if (key.shift) add(KeyModifier.SHIFT)
            if (key.alt) add(KeyModifier.ALT)
            if (key.ctrl) add(KeyModifier.CTRL)
        }
    return chordKeyOf(key.keyCode)?.let { KeyChord(it, modifiers) }
}

private fun chordKeyOf(keyCode: Int): ChordKey? {
    val letter = keyCode.toChar().takeIf { it in 'A'..'Z' || it in '0'..'9' }
    return NAMED_KEYS[keyCode] ?: letter?.let { ChordKey.Letter(it.lowercaseChar()) }
}

/**
 * Bridge of ⌘ keys from a focused terminal to the app shortcuts (AM-503, decision 9): ⌘K opens the palette,
 * any other chord runs the registry command bound to exactly that one chord, in [scope].
 */
internal class TerminalKeys(
    private val registry: CommandRegistry,
    private val palette: CommandPaletteController,
    private val scope: CoroutineScope,
) {
    /** @return true when the key was handled and must not reach the terminal. */
    fun onKey(key: TerminalKey): Boolean {
        val chord = chordOf(key)?.takeIf { KeyModifier.META in it.modifiers && it.key !in TEXT_EDITING }
        return chord != null && (openPalette(chord) || runCommand(chord))
    }

    private fun openPalette(chord: KeyChord): Boolean {
        if (Shortcut.of(chord) != AppShortcuts.PALETTE) return false
        palette.open()
        return true
    }

    private fun runCommand(chord: KeyChord): Boolean {
        val command = registry.commands.value.firstOrNull { it.shortcut?.chords == listOf(chord) } ?: return false
        scope.launch { command.run() }
        return true
    }
}
