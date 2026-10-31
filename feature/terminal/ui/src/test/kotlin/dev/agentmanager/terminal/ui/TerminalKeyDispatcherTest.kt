package dev.agentmanager.terminal.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel

/** ⌘ keys in a focused terminal widget (ADR 0012, condition 3; AM-503 plan, decision 8). No display needed. */
class TerminalKeyDispatcherTest {
    private val terminal = JPanel()
    private val inner = JPanel().also { terminal.add(it) }
    private val elsewhere = JPanel()
    private val ran = mutableListOf<PaneCommand>()
    private val appKeys = mutableListOf<TerminalKey>()
    private var appAnswer = false
    private var focus: Component? = inner

    private val dispatcher =
        TerminalKeyDispatcher(
            components = { listOf(terminal) },
            run = { ran += it },
            onAppKey = {
                appKeys += it
                appAnswer
            },
            focusOwner = { focus },
        )

    private fun press(
        keyCode: Int,
        modifiers: Int = 0,
        id: Int = KeyEvent.KEY_PRESSED,
    ): Boolean = dispatcher.dispatchKeyEvent(KeyEvent(inner, id, 0L, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED))

    @Test
    fun `pane shortcut in a terminal runs the pane command`() {
        press(KeyEvent.VK_D, InputEvent.META_DOWN_MASK) shouldBe true
        press(KeyEvent.VK_D, InputEvent.META_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK) shouldBe true
        press(KeyEvent.VK_RIGHT, InputEvent.META_DOWN_MASK or InputEvent.ALT_DOWN_MASK) shouldBe true
        press(KeyEvent.VK_ENTER, InputEvent.META_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK) shouldBe true

        ran shouldBe
            listOf(
                PaneCommand.Split(SplitAxis.SIDE_BY_SIDE),
                PaneCommand.Split(SplitAxis.STACKED),
                PaneCommand.FocusNext,
                PaneCommand.ToggleMaximized,
            )
        appKeys shouldBe emptyList()
    }

    @Test
    fun `meta F in a terminal runs Find and is consumed`() {
        press(KeyEvent.VK_F, InputEvent.META_DOWN_MASK) shouldBe true

        ran shouldBe listOf(PaneCommand.Find)
        appKeys shouldBe emptyList()
    }

    @Test
    fun `other meta keys go to onAppKey`() {
        press(KeyEvent.VK_K, InputEvent.META_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK)

        appKeys shouldBe listOf(TerminalKey(KeyEvent.VK_K, meta = true, shift = true, alt = false, ctrl = false))
        ran shouldBe emptyList()
    }

    @Test
    fun `consumed only when onAppKey returns true`() {
        appAnswer = false
        press(KeyEvent.VK_C, InputEvent.META_DOWN_MASK) shouldBe false

        appAnswer = true
        press(KeyEvent.VK_K, InputEvent.META_DOWN_MASK) shouldBe true

        appKeys.size shouldBe 2
    }

    @Test
    fun `keys without meta stay with the terminal`() {
        appAnswer = true

        press(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK) shouldBe false
        press(KeyEvent.VK_A) shouldBe false
        press(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK) shouldBe false

        ran shouldBe emptyList()
        appKeys shouldBe emptyList()
    }

    @Test
    fun `keys outside terminals are ignored`() {
        appAnswer = true
        focus = elsewhere
        press(KeyEvent.VK_D, InputEvent.META_DOWN_MASK) shouldBe false
        press(KeyEvent.VK_K, InputEvent.META_DOWN_MASK) shouldBe false

        focus = null
        press(KeyEvent.VK_D, InputEvent.META_DOWN_MASK) shouldBe false

        ran shouldBe emptyList()
        appKeys shouldBe emptyList()
    }

    @Test
    fun `the widget itself counts as a terminal`() {
        focus = terminal

        press(KeyEvent.VK_D, InputEvent.META_DOWN_MASK) shouldBe true

        ran shouldBe listOf(PaneCommand.Split(SplitAxis.SIDE_BY_SIDE))
    }

    @Test
    fun `key released is ignored`() {
        appAnswer = true

        press(KeyEvent.VK_D, InputEvent.META_DOWN_MASK, KeyEvent.KEY_RELEASED) shouldBe false
        press(KeyEvent.VK_K, InputEvent.META_DOWN_MASK, KeyEvent.KEY_RELEASED) shouldBe false

        ran shouldBe emptyList()
        appKeys shouldBe emptyList()
    }
}
