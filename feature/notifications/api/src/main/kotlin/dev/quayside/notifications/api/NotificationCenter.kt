package dev.quayside.notifications.api

import dev.quayside.core.domain.SessionId
import kotlinx.coroutines.flow.SharedFlow

/**
 * One notification to deliver on one [channel] (FR-N1), decided from the user's [NotificationSettings].
 * The adapters of the channels (popup, voice, sound, Dock badge) consume them.
 *
 * @property session the session it is about; null when it is not about a session (a terminal bell).
 */
public data class NotificationDelivery(
    val channel: NotificationChannel,
    val kind: NotificationKind,
    val session: SessionId?,
)

/**
 * Turns what happens in the app into [NotificationDelivery]s. Threading: safe to call from any dispatcher.
 * Each function reads the current [NotificationSettingsRepo.settings] when it is called.
 */
public interface NotificationCenter {
    /**
     * The deliveries decided so far. Hot, no replay: a delivery with no collector is lost, and a collector that
     * falls behind loses the oldest ones. Until the channel adapters exist (AM-702, AM-703) nothing collects it.
     */
    public val deliveries: SharedFlow<NotificationDelivery>

    /** A terminal rang its bell (AM-509): emits a delivery per channel chosen for [NotificationKind.TERMINAL_BELL]. */
    public suspend fun notifyTerminalBell()
}
