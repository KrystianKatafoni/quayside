package dev.quayside.notifications.impl

import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.notifications.api.NotificationCenter
import dev.quayside.notifications.api.NotificationSettingsRepo
import dev.quayside.sessions.api.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * The [NotificationCenter] that decides from [settings] (read at every event, so a change applies at once).
 * Collection of [events] starts before this returns, so no event after the call is missed; it, the popup timers and
 * the mutes live in [scope], as long as the app. The long parameter list is deliberate: `impl` may expose only
 * factories, so there is no public settings type.
 *
 * @param events the session monitor's events; without them only terminal bells notify.
 * @param projectName display name of a session's project for its popup; null when unknown.
 * @param onAttach opens the terminal of a session (the popup's Attach action); runs on the thread of the caller of
 *   `onAction`.
 * @param clock times the 15 minutes of a muted session.
 */
@Suppress("LongParameterList")
public fun notificationCenterModule(
    settings: NotificationSettingsRepo,
    scope: CoroutineScope,
    clock: Clock,
    events: Flow<SessionEvent> = emptyFlow(),
    projectName: (ProjectId?) -> String? = { null },
    onAttach: (SessionId) -> Unit = {},
): NotificationCenter {
    val manager = PopupManager(settings.settings, scope, clock, onAttach)
    val center = DefaultNotificationCenter(settings, manager, projectName)
    scope.launch(start = CoroutineStart.UNDISPATCHED) { events.collect(center::onSessionEvent) }
    return center
}
