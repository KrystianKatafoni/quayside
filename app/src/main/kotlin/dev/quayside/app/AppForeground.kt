package dev.quayside.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.KeyboardFocusManager

/**
 * Whether a window of the app is the active one (FR-N5): the voice stays silent then. Follows the `activeWindow`
 * property of [manager]; the popup window is not focusable, so it does not count. Lives as long as the app.
 */
internal class AppForeground(
    manager: KeyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager(),
) {
    private val state = MutableStateFlow(manager.activeWindow != null)

    /** True while a window of the app is active. */
    val active: StateFlow<Boolean> = state.asStateFlow()

    init {
        manager.addPropertyChangeListener(ACTIVE_WINDOW) { state.value = it.newValue != null }
    }

    private companion object {
        const val ACTIVE_WINDOW = "activeWindow"
    }
}
