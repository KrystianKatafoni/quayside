package dev.quayside.app

import dev.quayside.core.domain.SessionId
import dev.quayside.notifications.api.NotificationCenter
import dev.quayside.notifications.api.NotificationSettingsRepo
import dev.quayside.notifications.impl.notificationCenterModule
import dev.quayside.notifications.ui.PopupController
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.SessionsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

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

/** The notification center with the popup controller of the popup window and the [attach] bridge (AM-702). */
internal class NotificationDeps(
    val center: NotificationCenter,
    val popups: PopupController,
    val attach: AttachBridge,
)

/**
 * The notification center fed by the session events of [sessions]; popups show the project name from [projects]. Its
 * scope lives as long as the app (AM-702).
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
            sessions.monitor.events,
            { id -> projects.projects.value.firstOrNull { it.id == id }?.displayName },
            attach::attach,
        )
    return NotificationDeps(center, PopupController(center, settings, scope), attach)
}
