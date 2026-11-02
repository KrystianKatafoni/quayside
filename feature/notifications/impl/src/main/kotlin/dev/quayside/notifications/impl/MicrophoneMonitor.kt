package dev.quayside.notifications.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether any app records from the microphone (FR-N5). */
internal interface MicrophoneMonitor {
    val active: StateFlow<Boolean>
}

/** A monitor that never sees the microphone in use; the real one follows ADR 0018. */
internal object NoMicrophone : MicrophoneMonitor {
    override val active: StateFlow<Boolean> = MutableStateFlow(false)
}
