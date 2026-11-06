package dev.quayside.app

import dev.quayside.core.domain.SessionId
import dev.quayside.notifications.api.NotificationCenter
import dev.quayside.notifications.api.NotificationSettingsRepo
import dev.quayside.notifications.api.VoiceCatalog
import dev.quayside.notifications.api.VoicePreview
import dev.quayside.notifications.impl.notificationCenterModule
import dev.quayside.notifications.impl.voiceCatalogModule
import dev.quayside.notifications.impl.voicePreviewModule
import dev.quayside.notifications.ui.PopupController
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.SessionsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.time.ZoneId

/**
 * Where the popup action Attach goes. The center lives longer than the window, so it calls [attach]; the window sets
 * [handler] while it is shown (and clears it when it closes), and Attach is a no-op without it.
 */
internal class AttachBridge {
    @Volatile
    var handler: ((SessionId) -> Unit)? = null

    /** Asks the window to show the attach tab of [session]. Safe to call from any thread. */
    fun attach(session: SessionId) {
        handler?.invoke(session)
    }
}

/**
 * The notification center with the popup controller of the popup window, the [attach] bridge (AM-702) and the
 * [settings] repo behind the settings dialog (AM-704).
 */
internal class NotificationDeps(
    val center: NotificationCenter,
    val popups: PopupController,
    val attach: AttachBridge,
    val settings: NotificationSettingsRepo,
    val voices: VoiceCatalog,
    val preview: VoicePreview,
)

/**
 * The notification center fed by the session events of [sessions]; popups show the project name from [projects]. Its
 * scope lives as long as the app. The voice speaks through `core.process` while no window of the app is active
 * (AM-702, AM-703); the sound plays through `core.process` too, also while a window is active (AM-812).
 */
internal fun notificationDeps(
    core: CoreDeps,
    settings: NotificationSettingsRepo,
    sessions: SessionsApi,
    projects: ProjectsApi,
): NotificationDeps {
    val scope = CoroutineScope(SupervisorJob() + core.dispatchers.default)
    val attach = AttachBridge()
    val center =
        notificationCenterModule(
            settings,
            scope,
            core.clock,
            ZoneId::systemDefault,
            sessions.monitor.events,
            { id ->
                projects.projects.value
                    .firstOrNull { it.id == id }
                    ?.displayName
            },
            attach::attach,
            core.process,
            AppForeground().active,
            core.logger,
        )
    val voices = voiceCatalogModule(core.process, core.homeDir)
    val preview = voicePreviewModule(core.process, core.homeDir)
    return NotificationDeps(center, PopupController(center, settings, scope), attach, settings, voices, preview)
}
