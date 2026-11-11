package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationError
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.ConversationPage
import dev.quayside.agentruntime.api.HistoryCursor
import dev.quayside.agentruntime.api.HistoryError
import dev.quayside.agentruntime.api.PastConversation
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.SessionHistory
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.debug
import dev.quayside.core.domain.map
import dev.quayside.core.domain.warn
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.HistorySettingsError
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How often the session files are scanned again (FR-P9). */
internal val HISTORY_INTERVAL = 60.seconds

/** Result of the last good scan with the project dirs it was made for. */
private data class ScanResult(
    val past: List<PastConversation>,
    val dirs: Map<ProjectId, Path>,
)

/**
 * When to scan the Claude Code session files and what the last good scan said (FR-P9, ADR 0032). [start] scans at
 * once, every [HISTORY_INTERVAL], and at once after a change of the project dirs or of the maximum age; a change
 * of the count limit or of the hidden sessions only merges the last scan again. A failed scan keeps the last
 * result and logs one warning until a scan works again. Logs carry counts and fixed categories only (ENG-C8).
 */
internal class HistoryScan(
    private val history: SessionHistory,
    val settings: SessionHistorySettingsRepo,
    private val projects: ProjectsApi,
    private val clock: Clock,
    private val logger: Logger,
) {
    @Volatile private var last: ScanResult? = null

    @Volatile private var failing = false
    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    /** Pure merge of the last scan with [supervisor] under the current settings and project dirs. */
    fun merge(supervisor: List<RuntimeSession>): DiskMerge {
        val scanned = last ?: return DiskMerge(emptyList(), emptyMap())
        val current = projects.projects.value.associate { it.id to it.dir }
        val dirs = scanned.dirs.filter { (id, dir) -> current[id] == dir }
        return DiskSessions.merge(scanned.past, supervisor, dirs, settings.settings.value)
    }

    /**
     * Whether the session file of the disk session [disk] is still there: a fresh `SessionHistory.list` of its start
     * directory (no age limit) holds its UUID with the same start directory. [HistoryError] when listing fails.
     */
    suspend fun stillOnDisk(disk: Session): Outcome<Boolean, HistoryError> =
        history.list(listOf(disk.cwd), since = null).map { past ->
            past.any { it.conversationId.value == disk.id.value && it.startCwd == disk.cwd }
        }

    /** One page of the messages of the disk session [disk] from its session file (ADR 0034). */
    suspend fun conversation(
        disk: Session,
        before: HistoryCursor?,
        limit: Int,
    ): Outcome<ConversationPage, ConversationError> =
        history.conversation(ConversationId(disk.id.value), disk.cwd, before, limit)

    /** Adds [id] to the hidden sessions and saves; the session file stays on disk (ADR 0032). */
    suspend fun hide(id: SessionId): Outcome<Unit, HistorySettingsError> =
        settings.update { it.copy(hidden = it.hidden + id) }.map { }

    /**
     * Starts the watchers of the project dirs and of the settings and the scan loop in [scope]; [publish] is called
     * after each change of the result. The watchers start undispatched, so no change after `start` is lost.
     */
    fun start(
        scope: CoroutineScope,
        publish: suspend () -> Unit,
    ) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            projects.projects
                .map { list -> list.associate { it.id to it.dir } }
                .distinctUntilChanged()
                .drop(1)
                .collect { wakeups.trySend(Unit) }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { watchSettings(publish) }
        scope.launch {
            while (true) {
                if (scan()) publish()
                withTimeoutOrNull(HISTORY_INTERVAL) { wakeups.receive() }
            }
        }
    }

    private suspend fun watchSettings(publish: suspend () -> Unit) {
        var before = settings.settings.value
        settings.settings.collect { now ->
            if (now == before) return@collect
            val ageChanged = now.maxAge != before.maxAge
            before = now
            if (ageChanged) wakeups.trySend(Unit) else publish()
        }
    }

    /** Scans once; true when the result changed. A failure keeps the last result. */
    private suspend fun scan(): Boolean {
        val dirs = projects.projects.value.associate { it.id to it.dir }
        val since =
            settings.settings.value.maxAge.days
                ?.let { clock.now().minus(Duration.ofDays(it.toLong())) }
        return when (val listed = history.list(dirs.values.toList(), since)) {
            is Outcome.Ok -> {
                failing = false
                last = ScanResult(listed.value, dirs)
                logger.debug("Session files scanned", "count" to listed.value.size)
                true
            }

            is Outcome.Err -> {
                if (!failing) logger.warn("Session files scan failed", "problem" to listed.error.name)
                failing = true
                false
            }
        }
    }
}
