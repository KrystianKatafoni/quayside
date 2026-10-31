package dev.quayside.app

import androidx.compose.ui.input.key.KeyEvent

/** Bridge from `Window(onKeyEvent)` to the palette controller, which is created inside the window (ADR 0011). */
internal class WindowKeys {
    @Volatile
    private var handler: ((KeyEvent) -> Boolean)? = null

    /** Sends keys to [handler] until the returned handle is closed (closing twice is harmless). */
    fun attach(handler: (KeyEvent) -> Boolean): AutoCloseable {
        this.handler = handler
        return AutoCloseable { if (this.handler === handler) this.handler = null }
    }

    /** @return false without a handler. */
    fun onKeyEvent(event: KeyEvent): Boolean = handler?.invoke(event) ?: false
}
