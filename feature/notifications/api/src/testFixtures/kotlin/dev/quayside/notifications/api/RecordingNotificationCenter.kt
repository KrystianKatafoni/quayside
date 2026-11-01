package dev.quayside.notifications.api

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * [NotificationCenter] for tests of other modules: counts the calls and applies no policy, so it is not a subject of
 * a contract. [deliveries] never emits; [popups] is whatever the test sets with [show].
 */
public class RecordingNotificationCenter : NotificationCenter {
    private val rung = AtomicInteger()

    override val deliveries: SharedFlow<NotificationDelivery> = MutableSharedFlow<NotificationDelivery>().asSharedFlow()

    private val shown = MutableStateFlow<List<PopupModel>>(emptyList())
    private val actions = CopyOnWriteArrayList<Pair<PopupId, PopupAction>>()

    override val popups: StateFlow<List<PopupModel>> = shown

    /** Every [onAction] call so far, in order. */
    public val received: List<Pair<PopupId, PopupAction>> get() = actions.toList()

    /** Replaces [popups]. */
    public fun show(vararg models: PopupModel) {
        shown.value = models.toList()
    }

    override suspend fun onAction(
        popup: PopupId,
        action: PopupAction,
    ) {
        actions += popup to action
    }

    /** How many times [notifyTerminalBell] was called. */
    public val terminalBells: Int get() = rung.get()

    override suspend fun notifyTerminalBell() {
        rung.incrementAndGet()
    }
}
