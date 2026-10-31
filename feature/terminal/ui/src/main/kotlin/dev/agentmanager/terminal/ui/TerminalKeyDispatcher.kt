package dev.agentmanager.terminal.ui

import java.awt.Component
import java.awt.KeyEventDispatcher
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * ⌘ keys pressed while a terminal widget owns the focus (ADR 0012, condition 3). [components]: widgets of all
 * tabs; [focusOwner]: the AWT focus owner (injected in tests). Pane commands go to [run], other ⌘ keys to [onAppKey];
 * a key is consumed when handled by [run] or when [onAppKey] returns true. Keys without ⌘ are never touched.
 */
internal class TerminalKeyDispatcher(
    private val components: () -> List<JComponent>,
    private val run: (PaneCommand) -> Unit,
    private val onAppKey: (TerminalKey) -> Boolean,
    private val focusOwner: () -> Component?,
) : KeyEventDispatcher {
    override fun dispatchKeyEvent(e: KeyEvent): Boolean = isMetaPress(e) && inTerminal() && handle(e.toTerminalKey())

    private fun isMetaPress(e: KeyEvent): Boolean =
        e.id == KeyEvent.KEY_PRESSED && e.modifiersEx and InputEvent.META_DOWN_MASK != 0

    private fun handle(key: TerminalKey): Boolean {
        val command = TerminalShortcuts.commandOf(key) ?: return onAppKey(key)
        run(command)
        return true
    }

    private fun inTerminal(): Boolean {
        val owner = focusOwner() ?: return false
        return components().any { SwingUtilities.isDescendingFrom(owner, it) }
    }

    private fun KeyEvent.toTerminalKey(): TerminalKey =
        TerminalKey(
            keyCode = keyCode,
            meta = true,
            shift = isShiftDown,
            alt = isAltDown,
            ctrl = isControlDown,
        )
}
