package dev.quayside.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.amScrim
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * The bug of AM-803 (ADR 0012): over an embedded Swing view a Compose popup got neither clicks nor keys. A real
 * AWT window is the only place where this shows; skipped without a display.
 */
@Tag("awt-window")
class ModalOverSwingWindowTest {
    private val area = JTextArea("terminal")
    private var open by mutableStateOf(value = false)
    private var dismissed = 0
    private var clicks = 0

    private fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    private fun waitFor(condition: () -> Boolean) {
        repeat(times = 100) { if (!condition()) Thread.sleep(50) }
    }

    private fun focusOwner(): Component? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner

    private fun showWindow(): ComposeWindow {
        lateinit var window: ComposeWindow
        onEdt {
            window = ComposeWindow()
            window.setSize(800, 600)
            window.setContent {
                AmTestTheme {
                    Box(Modifier.fillMaxSize()) { EmbeddedWidget(area) }
                    if (open) Dialog()
                }
            }
            window.isVisible = true
        }
        waitFor { area.isShowing }
        onEdt { area.requestFocusInWindow() }
        waitFor { focusOwner() === area }
        return window
    }

    /** Like the dialogs of the app: a focusable popup whose control asks for the Compose focus when it opens. */
    @androidx.compose.runtime.Composable
    private fun Dialog() {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Popup(onDismissRequest = {}, properties = PopupProperties(focusable = true)) {
            Box(Modifier.fillMaxSize().amScrim(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(100.dp)
                        .background(Color.Red)
                        .focusRequester(focus)
                        .onPreviewKeyEvent {
                            it.type == KeyEventType.KeyDown && it.key == Key.Escape && ++dismissed > 0
                        }.focusable()
                        .clickable { clicks++ },
                )
            }
        }
    }

    private fun send(
        target: Component,
        event: (Component) -> java.awt.AWTEvent,
    ) = onEdt { target.dispatchEvent(event(target)) }

    private fun deepestAtCentre(window: ComposeWindow): Component? {
        val pane = window.layeredPane
        return SwingUtilities.getDeepestComponentAt(pane, pane.width / 2, pane.height / 2)
    }

    /** Posts the click to [target] (the canvas of the layer that owns the focus) at the centre of the window. */
    private fun clickAtCentre(target: Component) {
        onEdt {
            val x = target.width / 2
            val y = target.height / 2
            for (id in listOf(
                MouseEvent.MOUSE_ENTERED,
                MouseEvent.MOUSE_MOVED,
                MouseEvent.MOUSE_PRESSED,
                MouseEvent.MOUSE_RELEASED,
                MouseEvent.MOUSE_CLICKED,
            )) {
                val mask = if (id == MouseEvent.MOUSE_RELEASED) 0 else InputEvent.BUTTON1_DOWN_MASK
                target.dispatchEvent(
                    MouseEvent(target, id, System.currentTimeMillis(), mask, x, y, 1, false, MouseEvent.BUTTON1),
                )
            }
        }
    }

    private fun withModal(check: (ComposeWindow, Component) -> Unit) {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val window = showWindow()
        try {
            // Given a terminal view that owns the focus, when a modal layer opens over it
            open = true
            waitFor { focusOwner()?.javaClass?.name?.contains("Skia") == true }
            val owner = focusOwner()
            owner shouldNotBe null
            check(window, owner!!)
        } finally {
            onEdt { window.dispose() }
        }
    }

    @Test
    fun `Escape sent to the focus owner reaches the modal layer, not the terminal`() =
        withModal { _, owner ->
            send(owner) {
                KeyEvent(
                    it,
                    KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(),
                    0,
                    KeyEvent.VK_ESCAPE,
                    KeyEvent.CHAR_UNDEFINED,
                )
            }
            waitFor { dismissed == 1 }
            dismissed shouldBe 1
        }

    @Test
    fun `a click at the centre reaches the layer control and the terminal is not hit under the layer`() =
        withModal { window, owner ->
            (deepestAtCentre(window) === area) shouldBe false
            clickAtCentre(owner)
            waitFor { clicks == 1 }
            clicks shouldBe 1
        }

    @Test
    fun `the terminal is hit again when the layer is gone`() =
        withModal { window, _ ->
            open = false
            var hit: Component? = null
            waitFor {
                onEdt { hit = deepestAtCentre(window) }
                hit === area
            }
            (hit === area) shouldBe true
        }
}
