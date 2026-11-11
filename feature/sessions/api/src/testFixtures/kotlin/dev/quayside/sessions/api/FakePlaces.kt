package dev.quayside.sessions.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Path

/**
 * The places of [FakeSessions] (FR-P9, AM-314, ADR 0033): the subfolders of the world's projects, the choices saved in
 * the [settings] and the rules that turn them into [Session.assignedDir]. Same rules as `sessions:impl`: a saved place
 * counts only for the project it was picked in and only for a name that is exactly one of the project's subfolders.
 * The fake checks only `in subfolders`, not the rule "a direct child of the project directory" (like `startDir`).
 */
internal class FakePlaces(
    projects: List<WorldProject>,
    private val settings: FakeSessionHistorySettings,
) {
    private val worldProjects = MutableStateFlow(projects.associateBy { it.id })

    /** Live session id to the conversation it resumed (the real system knows it from the supervisor). */
    private val resumed = MutableStateFlow(emptyMap<SessionId, SessionId>())

    fun project(id: ProjectId): WorldProject? = worldProjects.value[id]

    fun setSubfolders(
        project: ProjectId,
        names: List<String>,
    ) {
        worldProjects.update { all ->
            all.mapValues { (id, p) -> if (id == project) p.copy(subfolders = names) else p }
        }
    }

    fun resumed(
        live: SessionId,
        conversation: SessionId,
    ) {
        resumed.update { it + (live to conversation) }
    }

    /** [Session.assignedDir] of every session in [sessions] by the saved places; [conversations] by supervisor id. */
    fun applied(
        sessions: List<Session>,
        conversations: Map<SessionId, String>,
    ): List<Session> {
        val stored = settings.settings.value.places
        return sessions.map { session ->
            val keys = keysOf(session, conversations)
            val place = keys.firstNotNullOfOrNull { stored[it] }
            session.copy(assignedDir = place?.let { dirOf(session, it) })
        }
    }

    /** Saves (or clears) the place of [session]; see [SessionCommands.assignPlace]. */
    suspend fun assign(
        id: SessionId,
        session: Session?,
        place: PlaceAssignment?,
        conversations: Map<SessionId, String>,
    ): Outcome<Unit, SessionError> {
        val project = session?.project?.let { project(it) } ?: return Outcome.Err(SessionError.UnknownSession(id))
        if (place is PlaceAssignment.Subfolder && place.name !in project.subfolders) {
            return Outcome.Err(SessionError.SubfolderUnavailable(project.id, SubfolderProblem.NOT_IN_PROJECT))
        }
        val keys = keysOf(session, conversations)
        if (place == null && keys.none { it in settings.settings.value.places }) return Outcome.Ok(Unit)
        val key = keys.first()
        val saved =
            settings.update { current ->
                val without = current.places.filterKeys { it !in keys }
                current.copy(places = if (place == null) without else without + (key to StoredPlace(project.id, place)))
            }
        return when (saved) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Err -> Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
        }
    }

    /** The conversation UUID first, then the short id. */
    private fun keysOf(
        session: Session,
        conversations: Map<SessionId, String>,
    ): List<SessionId> {
        val uuid = (conversations[session.id]?.let(::SessionId) ?: resumed.value[session.id])
        return listOfNotNull(uuid?.takeIf { session.origin == SessionOrigin.SUPERVISOR }, session.id).distinct()
    }

    private fun dirOf(
        session: Session,
        stored: StoredPlace,
    ): Path? {
        val project = project(stored.project)?.takeIf { it.id == session.project } ?: return null
        return when (val place = stored.place) {
            PlaceAssignment.ProjectRoot -> project.dir
            is PlaceAssignment.Subfolder -> project.dir.resolve(place.name).takeIf { place.name in project.subfolders }
        }
    }

    /** [projectDir], or its subfolder [name] when it is one of the project's and not [WorldProject.missing]. */
    fun startDir(
        project: ProjectId,
        projectDir: Path,
        name: String?,
    ): Outcome<Path, SessionError> {
        if (name == null) return Outcome.Ok(projectDir)
        val registered = checkNotNull(project(project))
        val problem =
            when {
                name !in registered.subfolders -> SubfolderProblem.NOT_IN_PROJECT
                name in registered.missing -> SubfolderProblem.MISSING
                else -> return Outcome.Ok(projectDir.resolve(name))
            }
        return Outcome.Err(SessionError.SubfolderUnavailable(project, problem))
    }
}
