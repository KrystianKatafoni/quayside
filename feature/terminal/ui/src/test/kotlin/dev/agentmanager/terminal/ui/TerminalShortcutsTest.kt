package dev.agentmanager.terminal.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.event.KeyEvent
import java.util.stream.Stream

/** Pane keys of the terminal (plan AM-503, table 3). */
internal class TerminalShortcutsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("bound")
    fun `pane keys map to pane commands`(
        key: TerminalKey,
        command: PaneCommand,
    ) {
        TerminalShortcuts.commandOf(key) shouldBe command
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unbound")
    fun `other keys are left alone`(key: TerminalKey) {
        TerminalShortcuts.commandOf(key) shouldBe null
    }

    @Test
    fun `option alone does not switch panes`() {
        TerminalShortcuts.commandOf(key(KeyEvent.VK_RIGHT, alt = true)) shouldBe null
    }

    @Test
    fun `meta F opens search`() {
        TerminalShortcuts.commandOf(key(KeyEvent.VK_F, meta = true)) shouldBe PaneCommand.Find
    }

    @Test
    fun `meta shift F is not a shortcut`() {
        TerminalShortcuts.commandOf(key(KeyEvent.VK_F, meta = true, shift = true)) shouldBe null
    }

    companion object {
        @JvmStatic
        fun bound(): Stream<Arguments> =
            Stream.of(
                Arguments.of(key(KeyEvent.VK_D, meta = true), PaneCommand.Split(SplitAxis.SIDE_BY_SIDE)),
                Arguments.of(
                    key(KeyEvent.VK_D, meta = true, shift = true),
                    PaneCommand.Split(SplitAxis.STACKED),
                ),
                Arguments.of(
                    key(KeyEvent.VK_RIGHT, meta = true, alt = true),
                    PaneCommand.FocusNext,
                ),
                Arguments.of(
                    key(KeyEvent.VK_LEFT, meta = true, alt = true),
                    PaneCommand.FocusPrevious,
                ),
                Arguments.of(
                    key(KeyEvent.VK_ENTER, meta = true, shift = true),
                    PaneCommand.ToggleMaximized,
                ),
            )

        @JvmStatic
        fun unbound(): Stream<Arguments> =
            Stream.of(
                Arguments.of(key(KeyEvent.VK_RIGHT, meta = true)),
                Arguments.of(key(KeyEvent.VK_D, ctrl = true)),
                Arguments.of(key(KeyEvent.VK_K, meta = true)),
                Arguments.of(key(KeyEvent.VK_ENTER, meta = true)),
                Arguments.of(key(KeyEvent.VK_D)),
            )
    }
}
