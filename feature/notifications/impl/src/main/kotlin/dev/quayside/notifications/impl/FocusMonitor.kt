package dev.quayside.notifications.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether a macOS Focus is on (FR-N6). */
internal interface FocusMonitor {
    val active: StateFlow<Boolean>
}

/** A monitor that never sees a Focus; the real adapter is a separate task. */
internal object NoFocus : FocusMonitor {
    override val active: StateFlow<Boolean> = MutableStateFlow(false)
}
