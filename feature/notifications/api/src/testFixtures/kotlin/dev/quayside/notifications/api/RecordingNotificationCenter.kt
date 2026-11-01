package dev.quayside.notifications.api

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * [NotificationCenter] for tests of other modules: counts the calls and applies no policy, so it is not a subject of
 * a contract. [deliveries] never emits.
 */
public class RecordingNotificationCenter : NotificationCenter {
    private val rung = AtomicInteger()

    override val deliveries: SharedFlow<NotificationDelivery> = MutableSharedFlow<NotificationDelivery>().asSharedFlow()

    /** How many times [notifyTerminalBell] was called. */
    public val terminalBells: Int get() = rung.get()

    override suspend fun notifyTerminalBell() {
        rung.incrementAndGet()
    }
}
