package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionOrigin

/**
 * The selected session to attach to: `⏎` (AM-505) and `r` (AM-506, only when [waiting]). Holds its [session] id,
 * display [name] for the tab label and the registered [project] whose tab group gets the tab. [name] and [project]
 * are user data: log [session] only (ENG-C8).
 * @property waiting the session waits for the user ([dev.quayside.sessions.api.SessionState.isWaiting]): a
 *   question or a permission request.
 */
public data class AttachTarget(
    val session: SessionId,
    val name: String,
    val project: Project,
    val waiting: Boolean = false,
)

/** When `⏎` and `r` can attach (AM-505, AM-506, table 1). */
internal object SessionAttach {
    /** The target for [session], or null without a session, for a disk session, outside [projects] or in a dialog. */
    fun targetOf(
        session: Session?,
        projects: List<Project>,
        dialogOpen: Boolean,
    ): AttachTarget? {
        if (session == null || dialogOpen || session.origin == SessionOrigin.DISK) return null
        val project = projects.firstOrNull { it.id == session.project }
        return project?.let { AttachTarget(session.id, session.name, it, session.state.isWaiting) }
    }
}
