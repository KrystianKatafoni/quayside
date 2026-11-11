package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.StoredPlace
import dev.quayside.sessions.api.SubfolderProblem

/**
 * The places picked by the user (FR-P9, AM-314, ADR 0033) over the saved [settings]: lays them over the published
 * sessions as [Session.assignedDir] and saves a new choice. The rules themselves are the pure [SessionPlaces].
 */
internal class PlaceAssignments(
    private val settings: SessionHistorySettingsRepo,
    private val projects: ProjectsApi,
) {
    /** [snapshot] with [Session.assignedDir] of every session; [listing] gives the UUID of a supervisor session. */
    fun placed(
        snapshot: SessionsSnapshot,
        listing: List<RuntimeSession>,
    ): SessionsSnapshot {
        val conversations = listing.mapNotNull { s -> s.conversationId?.let { s.id to it } }.toMap()
        val sessions =
            SessionPlaces.placed(
                snapshot.sessions,
                settings.settings.value.places,
                projects.projects.value,
                conversations,
            )
        return snapshot.copy(sessions = sessions)
    }

    /** [events] with the sessions as [snapshot] shows them, so an event carries the assigned dir too. */
    fun placed(
        events: List<SessionEvent>,
        snapshot: SessionsSnapshot,
    ): List<SessionEvent> =
        events.map { event ->
            snapshot.sessions
                .firstOrNull { it.id == event.session.id }
                ?.let { SessionPlaces.withSession(event, it) } ?: event
        }

    /**
     * Saves [place] for [session] (null = back to the place by its directory); `Ok(true)` when the file changed,
     * `Ok(false)` for "Automatycznie" without an entry. A missing session or one outside every project is
     * [SessionError.UnknownSession] for [id]; a place that does not fit the project, [SessionError.SubfolderUnavailable].
     */
    suspend fun save(
        id: SessionId,
        session: Session?,
        conversation: ConversationId?,
        place: PlaceAssignment?,
    ): Outcome<Boolean, SessionError> {
        val project = projects.projects.value.firstOrNull { it.id == session?.project }
        return when {
            session == null || project == null -> {
                Outcome.Err(SessionError.UnknownSession(id))
            }

            place != null && !SessionPlaces.validate(place, project) -> {
                Outcome.Err(SessionError.SubfolderUnavailable(project.id, SubfolderProblem.NOT_IN_PROJECT))
            }

            place == null && SessionPlaces.find(settings.settings.value.places, session, conversation) == null -> {
                Outcome.Ok(false)
            }

            else -> {
                write(session, conversation, place?.let { StoredPlace(project.id, it) })
            }
        }
    }

    private suspend fun write(
        session: Session,
        conversation: ConversationId?,
        stored: StoredPlace?,
    ): Outcome<Boolean, SessionError> {
        val key = SessionPlaces.key(session, conversation)
        val saved = settings.update { it.copy(places = it.places.without(session.id, key).with(key, stored)) }
        return when (saved) {
            is Outcome.Ok -> Outcome.Ok(true)
            is Outcome.Err -> Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
        }
    }

    private fun Map<SessionId, StoredPlace>.without(
        short: SessionId,
        key: SessionId,
    ): Map<SessionId, StoredPlace> = this - short - key

    private fun Map<SessionId, StoredPlace>.with(
        key: SessionId,
        stored: StoredPlace?,
    ): Map<SessionId, StoredPlace> = if (stored == null) this else this + (key to stored)
}
