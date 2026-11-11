package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectGroups
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.StoredPlace
import java.nio.file.Path

/** Pure rules of the manual place of a session (FR-P9, AM-314, ADR 0033). No IO. */
internal object SessionPlaces {
    /**
     * Key of the saved place of [session]: the conversation UUID when known ([conversation] of a supervisor session,
     * always the id of a disk session), else the short supervisor id.
     */
    fun key(
        session: Session,
        conversation: ConversationId?,
    ): SessionId =
        if (session.origin == SessionOrigin.SUPERVISOR && conversation != null) {
            SessionId(conversation.value)
        } else {
            session.id
        }

    /** The saved place of [session] in [places]: the entry under the UUID wins over the one under the short id. */
    fun find(
        places: Map<SessionId, StoredPlace>,
        session: Session,
        conversation: ConversationId?,
    ): StoredPlace? = places[key(session, conversation)] ?: places[session.id]

    /**
     * [Session.assignedDir] for [stored]: null without an entry, for a session of another project than the entry's,
     * and when the entry no longer fits [projects] (see [validate]).
     */
    fun resolve(
        stored: StoredPlace?,
        session: Session,
        projects: List<Project>,
    ): Path? {
        val project = projects.firstOrNull { it.id == session.project && it.id == stored?.project }
        return if (stored == null || project == null) null else dirOf(stored.place, project)
    }

    /** [place] is the project root, or exactly (letter case included) one of the project's direct-child subfolders. */
    fun validate(
        place: PlaceAssignment,
        project: Project,
    ): Boolean = dirOf(place, project) != null

    /**
     * The directory of [name] when it is exactly (letter case included) one of `Project.subfolders` and a direct child
     * of `Project.dir` (a name such as `..` or `a/b` is not); else null.
     */
    fun subfolderDir(
        project: Project,
        name: String,
    ): Path? {
        val found = project.subfolders.firstOrNull { it.value == name } ?: return null
        val dir = ProjectGroups.dirOf(project, found)
        return dir.takeIf { it.normalize().parent == project.dir.normalize() }
    }

    /** [Session.assignedDir] of every session of [sessions]; [conversations] gives the UUID of a supervisor session. */
    fun placed(
        sessions: List<Session>,
        places: Map<SessionId, StoredPlace>,
        projects: List<Project>,
        conversations: Map<SessionId, ConversationId>,
    ): List<Session> =
        sessions.map {
            it.copy(assignedDir = resolve(find(places, it, conversations[it.id]), it, projects))
        }

    /** [event] with [session] as published in the snapshot. */
    fun withSession(
        event: SessionEvent,
        session: Session,
    ): SessionEvent =
        when (event) {
            is SessionEvent.BecameBlocked -> event.copy(session = session)
            is SessionEvent.TurnFinished -> event.copy(session = session)
            is SessionEvent.Failed -> event.copy(session = session)
            is SessionEvent.PullRequestReady -> event.copy(session = session)
            is SessionEvent.RunningLong -> event.copy(session = session)
        }

    private fun dirOf(
        place: PlaceAssignment,
        project: Project,
    ): Path? =
        when (place) {
            PlaceAssignment.ProjectRoot -> project.dir
            is PlaceAssignment.Subfolder -> subfolderDir(project, place.name)
        }
}
