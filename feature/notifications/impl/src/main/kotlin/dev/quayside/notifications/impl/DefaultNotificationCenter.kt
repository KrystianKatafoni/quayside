package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationCenter
import dev.quayside.notifications.api.NotificationDelivery
import dev.quayside.notifications.api.NotificationSettingsRepo
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * [NotificationCenter] that decides with [decideTerminalBell] over the current settings of [settings] and publishes the
 * result. Emitting never suspends: a slow collector loses the oldest deliveries instead of blocking the caller.
 */
internal class DefaultNotificationCenter(
    private val settings: NotificationSettingsRepo,
) : NotificationCenter {
    private val flow =
        MutableSharedFlow<NotificationDelivery>(
            extraBufferCapacity = BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val deliveries: SharedFlow<NotificationDelivery> = flow.asSharedFlow()

    override suspend fun notifyTerminalBell() {
        decideTerminalBell(settings.settings.value).forEach { flow.tryEmit(it.toPublic()) }
    }

    private companion object {
        const val BUFFER = 64
    }
}

private fun Delivery.toPublic(): NotificationDelivery = NotificationDelivery(channel, kind, session?.id)
