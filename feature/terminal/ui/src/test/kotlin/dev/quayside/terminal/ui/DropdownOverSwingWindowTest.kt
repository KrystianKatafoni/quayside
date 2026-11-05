package dev.quayside.terminal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.quayside.core.designsystem.AmDropdown
import dev.quayside.core.designsystem.AmDropdownItems
import dev.quayside.core.designsystem.AmModalGate
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.LocalAmModalGate
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * AM-817 on the bug of AM-803 (ADR 0012): the list of an `AmDropdown` is a Compose popup, and over an embedded Swing
 * terminal a popup got neither clicks nor keys unless it counts as a modal layer in the gate. A real AWT window is the
 * only place where this shows; skipped without a display. The field sits in a bar over the terminal, so the list opens
 * over the terminal. Three options and no selection: a click on any row of the list picks something.
 */
@Tag("awt-window")
class DropdownOverSwingWindowTest {
    private val area = JTextArea("terminal")
    private val gate = AmModalGate()
    private val picks = CopyOnWriteArrayList<Int>()
    private val terminalKeys = CopyOnWriteArrayList<Int>()
    private val terminalMouse = CopyOnWriteArrayList<Int>()

    // Position of the field in the window in dp (= AWT points), written by onGloballyPositioned.
    @Volatile private var fieldLeft = 0f

    @Volatile private var fieldBottom = 0f
    private var dialogOpen by mutableStateOf(value = false)

    // written on the EDT, read by the test thread
    @Volatile private var dialogEscapes = 0

    @Volatile private var dismissals = 0

    private fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    private fun waitFor(condition: () -> Boolean) {
        repeat(times = 100) { if (!condition()) Thread.sleep(50) }
    }

    private fun focusOwner(): Component? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner

    private fun isSkia(component: Component?): Boolean = component?.javaClass?.name?.contains("Skia") == true

    @Composable
    private fun Field(modifier: Modifier = Modifier) {
        val density = LocalDensity.current
        AmDropdown(
            items = AmDropdownItems(listOf("First", "Second", "Third"), selected = -1, placeholder = "Pick one"),
            label = "Choice",
            onSelect = { picks += it },
            modifier =
                modifier.onGloballyPositioned {
                    val bounds = it.boundsInWindow()
                    fieldLeft = bounds.left / density.density
                    fieldBottom = bounds.bottom / density.density
                },
        )
    }

    /** Like the dialogs of the app, but without a scrim, so that the gate shows only whether the list is open. */
    @Composable
    private fun Dialog() {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Popup(onDismissRequest = { dismissals++ }, properties = PopupProperties(focusable = true)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                        if (escape) dialogEscapes++
                        escape
                    },
                contentAlignment = Alignment.Center,
            ) {
                Field(Modifier.focusRequester(focus))
            }
        }
    }

    private fun listenToTerminal() {
        area.addKeyListener(
            object : KeyListener {
                override fun keyPressed(e: KeyEvent) {
                    terminalKeys += e.keyCode
                }

                override fun keyReleased(e: KeyEvent) = Unit

                override fun keyTyped(e: KeyEvent) = Unit
            },
        )
        area.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    terminalMouse += e.id
                }
            },
        )
    }

    private fun showWindow(): ComposeWindow {
        lateinit var window: ComposeWindow
        onEdt {
            window = ComposeWindow()
            window.setSize(800, 600)
            listenToTerminal()
            window.setContent {
                AmTestTheme {
                    CompositionLocalProvider(LocalAmModalGate provides gate) {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxWidth()) { Field() }
                            Box(Modifier.fillMaxSize()) { EmbeddedWidget(area) }
                        }
                        if (dialogOpen) Dialog()
                    }
                }
            }
            window.isVisible = true
        }
        waitFor { area.isShowing }
        onEdt { area.requestFocusInWindow() }
        waitFor { focusOwner() === area }
        return window
    }

    private fun findCanvas(root: Component): Component? {
        // The children first: the root of the Skia hierarchy is a container without mouse listeners.
        return (root as? Container)?.components?.firstNotNullOfOrNull { findCanvas(it) }
            ?: root.takeIf { isSkia(it) }
    }

    private fun send(
        target: Component,
        event: (Component) -> java.awt.AWTEvent,
    ) = onEdt { target.dispatchEvent(event(target)) }

    private fun pressKey(
        target: Component,
        code: Int,
    ) {
        send(
            target,
        ) { KeyEvent(it, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED) }
        send(
            target,
        ) { KeyEvent(it, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED) }
    }

    /** Posts a click at ([x], [y]) of [target], the canvas of the layer that owns the focus. */
    private fun clickAt(
        target: Component,
        x: Int,
        y: Int,
    ) {
        onEdt {
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

    private fun deepestAt(
        window: ComposeWindow,
        x: Int,
        y: Int,
    ): Component? {
        var hit: Component? = null
        onEdt { hit = SwingUtilities.getDeepestComponentAt(window.layeredPane, x, y) }
        return hit
    }

    /** A point on the first rows of the list: under the field, a little inside from its left edge. */
    private fun listPoint(): Pair<Int, Int> = (fieldLeft + LIST_INSET).toInt() to (fieldBottom + LIST_INSET).toInt()

    /** Opens the list with a click on the field and returns the canvas that owns the focus then. */
    private fun openListOverTerminal(window: ComposeWindow): Component {
        val canvas = checkNotNull(findCanvas(window.contentPane)) { "no Skia canvas in the window" }
        waitFor { fieldBottom > 0f }
        clickAt(canvas, (fieldLeft + LIST_INSET).toInt(), (fieldBottom / 2).toInt())
        waitFor { gate.active }
        gate.active shouldBe true
        waitFor { isSkia(focusOwner()) }
        return checkNotNull(focusOwner()) { "no focus owner after the list opened" }
    }

    private fun withWindow(check: (ComposeWindow) -> Unit) {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val window = showWindow()
        try {
            val focused = focusOwner() === area
            // On CI a missing focus must not turn into silent lack of coverage; locally the test is skipped.
            if (System.getenv("CI") != null) {
                check(focused) { "the window did not get the keyboard focus on CI" }
            } else {
                assumeTrue(focused, "the window did not get the keyboard focus (no frontmost window)")
            }
            check(window)
        } finally {
            onEdt { window.dispose() }
        }
    }

    @Test
    fun `the open list over a terminal gets Escape and the terminal does not`() =
        withWindow { window ->
            val owner = openListOverTerminal(window)

            pressKey(owner, KeyEvent.VK_ESCAPE)

            waitFor { !gate.active }
            gate.active shouldBe false
            terminalKeys.size shouldBe 0
            picks.size shouldBe 0
        }

    @Test
    fun `a click on an option over the terminal selects it and does not hit the terminal`() =
        withWindow { window ->
            val owner = openListOverTerminal(window)
            val (x, y) = listPoint()
            (deepestAt(window, x, y) === area) shouldBe false

            clickAt(owner, x, y)

            waitFor { picks.isNotEmpty() }
            picks.size shouldBe 1
            waitFor { !gate.active }
            gate.active shouldBe false
            terminalMouse.size shouldBe 0
        }

    @Test
    fun `a click outside the list closes it and the terminal is hit again after it closes`() =
        withWindow { window ->
            val owner = openListOverTerminal(window)
            val x = window.layeredPane.width * 3 / 4
            val y = window.layeredPane.height * 3 / 4
            (deepestAt(window, x, y) === area) shouldBe false

            clickAt(owner, x, y)

            waitFor { !gate.active }
            gate.active shouldBe false
            picks.size shouldBe 0
            var hit: Component? = null
            waitFor {
                hit = deepestAt(window, x, y)
                hit === area
            }
            (hit === area) shouldBe true
        }

    @Test
    fun `a list opened in a dialog over the terminal closes on Escape and the dialog stays`() =
        withWindow { window ->
            // Given a dialog popup that owns the focus, with the field focused
            dialogOpen = true
            waitFor { isSkia(focusOwner()) }
            val owner = checkNotNull(focusOwner())
            waitFor { fieldBottom > 0f }

            // When the user opens the list with Enter (sent again until it shows) and presses Escape
            repeat(times = 5) {
                if (!gate.active) {
                    pressKey(owner, KeyEvent.VK_ENTER)
                    waitFor { gate.active }
                }
            }
            gate.active shouldBe true
            pressKey(owner, KeyEvent.VK_ESCAPE)
            waitFor { !gate.active }

            // Then the list is gone, the dialog heard nothing and did not ask to be dismissed
            gate.active shouldBe false
            dialogEscapes shouldBe 0
            dismissals shouldBe 0
            picks.size shouldBe 0
            (window.isShowing) shouldBe true

            // And the next Escape reaches the dialog, which is still there
            pressKey(owner, KeyEvent.VK_ESCAPE)
            waitFor { dialogEscapes == 1 }
            dialogEscapes shouldBe 1
        }

    private companion object {
        /** Dp inside the list from the left edge of the field and down from its bottom edge: on the first row. */
        const val LIST_INSET = 20
    }
}
