package dev.quayside.notifications.impl

import dev.quayside.core.domain.ProjectId
import dev.quayside.notifications.api.NotificationCenter
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationDelivery
import dev.quayside.notifications.api.NotificationSettingsRepo
import dev.quayside.notifications.api.PopupAction
import dev.quayside.notifications.api.PopupId
import dev.quayside.notifications.api.PopupModel
import dev.quayside.sessions.api.SessionEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * [NotificationCenter] that decides with [decide] and [decideTerminalBell] over the current settings of [settings],
 * publishes the deliveries, shows the popups of [popups] and hands voice and sound deliveries to [voice] and
 * [sound]. A muted session gets nothing on any channel. Emitting never suspends: a slow collector loses the oldest
 * deliveries instead of blocking the caller.
 *
 * @param projectName display name of a project for the popup; null when unknown.
 */
internal class DefaultNotificationCenter(
    private val settings: NotificationSettingsRepo,
    private val manager: PopupManager,
    private val projectName: (ProjectId?) -> String?,
    private val context: () -> DeliveryContext,
    private val voice: (Delivery, String?) -> Unit = { _, _ -> },
    private val sound: (Delivery) -> Unit = {},
) : NotificationCenter {
    private val flow =
        MutableSharedFlow<NotificationDelivery>(
            extraBufferCapacity = BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    override val deliveries: SharedFlow<NotificationDelivery> = flow.asSharedFlow()

    override val popups: StateFlow<List<PopupModel>> = manager.popups

    override suspend fun notifyTerminalBell() = handle(decideTerminalBell(settings.settings.value, context()))

    override suspend fun onAction(
        popup: PopupId,
        action: PopupAction,
    ) = manager.act(popup, action)

    /** A session changed state (the events of the session monitor). */
    fun onSessionEvent(event: SessionEvent) = handle(decide(event, settings.settings.value, context()))

    private fun handle(deliveries: List<Delivery>) {
        deliveries.filterNot { manager.isMuted(it.session?.id) }.forEach { delivery ->
            flow.tryEmit(NotificationDelivery(delivery.channel, delivery.kind, delivery.session?.id))
            when (delivery.channel) {
                NotificationChannel.POPUP -> manager.show(delivery, projectName(delivery.session?.project))
                NotificationChannel.VOICE -> voice(delivery, projectName(delivery.session?.project))
                NotificationChannel.SOUND -> sound(delivery)
                NotificationChannel.DOCK_BADGE -> Unit
            }
        }
    }

    private companion object {
        const val BUFFER = 64
    }
}
