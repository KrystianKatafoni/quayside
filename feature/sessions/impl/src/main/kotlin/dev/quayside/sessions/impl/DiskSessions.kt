package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.PastConversation
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.ProjectHistory
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionHistorySettings
import dev.quayside.sessions.api.SessionMeta
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SessionRules
import dev.quayside.sessions.api.SessionState
import java.nio.file.Path

/** Disk sessions of one scan, merged with the supervisor's list, and the per-project counts of them. */
internal data class DiskMerge(
    val sessions: List<Session>,
    val history: Map<ProjectId, ProjectHistory>,
)

/** Pure rules that turn [PastConversation]s into disk sessions (FR-P9, ADR 0032). No IO. */
internal object DiskSessions {
    /** Length of the short supervisor id: the start of the conversation UUID. */
    private const val SHORT_ID = 8

    /**
     * Leaves out conversations the supervisor knows ([RuntimeSession.conversationId] = UUID, or [RuntimeSession.id]
     * = the first 8 characters of the UUID), hidden ones and ones that started outside every project; the deepest
     * project owns a session; keeps the newest per project by `modifiedAt` up to the count limit; `history` has a
     * key for every project in [projects]. Sessions are ordered by `startedAt`, then id.
     */
    fun merge(
        past: List<PastConversation>,
        supervisor: List<RuntimeSession>,
        projects: Map<ProjectId, Path>,
        settings: SessionHistorySettings,
    ): DiskMerge {
        val knownIds = supervisor.mapNotNull { it.conversationId?.value }.toSet()
        val shortIds = supervisor.map { it.id.value }.toSet()
        val perProject =
            past
                .distinctBy { it.conversationId }
                .filter { it.conversationId.value !in knownIds && it.conversationId.value.take(SHORT_ID) !in shortIds }
                .filter { SessionId(it.conversationId.value) !in settings.hidden }
                .groupBy { SessionRules.projectFor(it.startCwd, projects) }
        val limit = settings.maxPerProject.count
        val kept =
            projects.keys.associateWith { project ->
                perProject[project].orEmpty().sortedWith(newestFirst).let {
                    it.take(limit) to
                        it.size - minOf(it.size, limit)
                }
            }
        val sessions =
            kept
                .flatMap { (project, pair) -> pair.first.map { it.toSession(project) } }
                .sortedWith(compareBy<Session> { it.startedAt }.thenBy { it.id.value })
        return DiskMerge(sessions, kept.mapValues { (_, pair) -> ProjectHistory(pair.first.size, pair.second) })
    }

    private val newestFirst =
        compareByDescending<PastConversation> { it.modifiedAt }.thenBy { it.conversationId.value }

    private fun PastConversation.toSession(project: ProjectId): Session =
        Session(
            id = SessionId(conversationId.value),
            project = project,
            name = title ?: conversationId.value.take(SHORT_ID),
            cwd = startCwd,
            state = SessionState.Stopped,
            startedAt = startedAt ?: modifiedAt,
            lastActivityAt = lastActivityAt ?: modifiedAt,
            summary = null,
            meta = SessionMeta(),
            origin = SessionOrigin.DISK,
        )
}
