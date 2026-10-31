package dev.agentmanager.app

import androidx.compose.ui.input.key.KeyEvent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.awt.Canvas
import java.awt.event.KeyEvent as AwtKeyEvent

/** The bridge from `Window(onKeyEvent)` to the palette controller (AM-409, decision 4). */
class WindowKeysTest {
    private val event =
        KeyEvent(AwtKeyEvent(Canvas(), AwtKeyEvent.KEY_PRESSED, 0L, 0, AwtKeyEvent.VK_J, 'j'))

    @Test
    fun `keys are not consumed before attach`() {
        WindowKeys().onKeyEvent(event) shouldBe false
    }

    @Test
    fun `keys reach the handler after attach`() {
        val keys = WindowKeys()
        val seen = mutableListOf<KeyEvent>()
        keys.attach {
            seen += it
            true
        }

        keys.onKeyEvent(event) shouldBe true

        seen shouldBe listOf(event)
    }

    @Test
    fun `the answer of the handler is passed on`() {
        val keys = WindowKeys()
        keys.attach { false }

        keys.onKeyEvent(event) shouldBe false
    }

    @Test
    fun `keys are not consumed after close`() {
        val keys = WindowKeys()
        val seen = mutableListOf<KeyEvent>()
        val handle =
            keys.attach {
                seen += it
                true
            }

        handle.close()

        keys.onKeyEvent(event) shouldBe false
        seen shouldBe emptyList()
        // Closing twice does nothing.
        handle.close()
    }
}
