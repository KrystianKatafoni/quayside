package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationCenter
import dev.quayside.notifications.api.NotificationSettingsRepo

/**
 * The [NotificationCenter] that decides from [settings] (read at every call, so a change applies at once).
 * Thread-safe and without resources to release.
 */
public fun notificationCenterModule(settings: NotificationSettingsRepo): NotificationCenter =
    DefaultNotificationCenter(settings)
