package dev.quayside.notifications.impl

import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.process.ProcessRunner
import dev.quayside.notifications.api.NotificationCenter
import dev.quayside.notifications.api.NotificationSettingsRepo
import dev.quayside.sessions.api.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.time.ZoneId

/**
 * The [NotificationCenter] that decides from [settings] (read at every event, so a change applies at once).
 * Collection of [events] starts before this returns, so no event after the call is missed; it, the popup timers and
 * the mutes live in [scope], as long as the app. The long parameter list is deliberate: `impl` may expose only
 * factories, so there is no public settings type. The macOS Focus is wired in as [NoFocus] for now (never on);
 * injecting a [FocusMonitor] comes with the Focus adapter (a follow-up task).
 *
 * @param events the session monitor's events; without them only terminal bells notify.
 * @param projectName display name of a session's project for its popup; null when unknown.
 * @param onAttach opens the terminal of a session (the popup's Attach action); runs on the thread of the caller of
 *   `onAction`.
 * @param clock times the 15 minutes of a muted session and the quiet hours.
 * @param zone the time zone of the quiet hours, read at every event.
 * @param process runs `say` for the voice channel; null means no voice.
 * @param appActive true while a window of the app is active; the voice is silent then (FR-N5).
 * @param logger receives the voice's log lines (a fixed category only, never the spoken text).
 */
@Suppress("LongParameterList")
public fun notificationCenterModule(
    settings: NotificationSettingsRepo,
    scope: CoroutineScope,
    clock: Clock,
    zone: () -> ZoneId,
    events: Flow<SessionEvent> = emptyFlow(),
    projectName: (ProjectId?) -> String? = { null },
    onAttach: (SessionId) -> Unit = {},
    process: ProcessRunner? = null,
    appActive: StateFlow<Boolean> = MutableStateFlow(false),
    logger: Logger = NoOpLogger,
): NotificationCenter {
    val manager = PopupManager(settings.settings, scope, clock, onAttach)
    val context = {
        DeliveryContext(
            appActive.value,
            NoMicrophone.active.value,
            localTimeAt(clock.now(), zone()),
            NoFocus.active.value,
        )
    }
    val notifier = voiceNotifier(process, settings, scope, context, logger)
    val center =
        DefaultNotificationCenter(settings, manager, projectName, context) { delivery, project ->
            notifier?.offer(delivery, project)
        }
    scope.launch(start = CoroutineStart.UNDISPATCHED) { events.collect(center::onSessionEvent) }
    return center
}

private fun voiceNotifier(
    process: ProcessRunner?,
    settings: NotificationSettingsRepo,
    scope: CoroutineScope,
    context: () -> DeliveryContext,
    logger: Logger,
): VoiceNotifier? =
    process?.let {
        val say = MacSayAdapter(it, Path.of(System.getProperty("user.home")))
        VoiceNotifier(say, settings.settings, scope, context, logger)
    }
