package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.debug
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.RecentOutput
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionMonitor
import dev.quayside.sessions.api.SessionsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path

/**
 * The polling loop (docs/11 §6, §7); [start] launches it and the project watcher in the given scope.
 *
 * Each listing is one [SessionsState.refresh]: every session (`under = null`), the current project dirs.
 * The wait after it comes from [schedule]; a change of the project dirs ends the wait at once. The loop
 * does not catch exceptions: the runtime returns errors as [Outcome] (ENG-I4), so an exception is a bug.
 */
internal class PollingSessionMonitor(
    private val projects: ProjectsApi,
    private val state: SessionsState,
    private val schedule: PollSchedule,
    private val logger: Logger,
    private val reader: RecentOutputReader,
) : SessionMonitor {
    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    override val snapshot: StateFlow<SessionsSnapshot> get() = state.snapshot

    override val events: Flow<SessionEvent> get() = state.events

    override suspend fun recentOutput(id: SessionId): Outcome<RecentOutput, SessionError> = reader.read(id)

    /**
     * Starts the project watcher and the loop; both stop when [scope] is cancelled. The watcher starts
     * undispatched, so its subscription and the baseline skipped by `drop(1)` are set before the loop can
     * list; a change right after [start] is never lost, also on a multi-threaded dispatcher.
     */
    fun start(scope: CoroutineScope) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            projects.projects
                .map { it.dirs() }
                .distinctUntilChanged()
                .drop(1)
                .collect { wakeups.trySend(Unit) }
        }
        scope.launch { poll() }
    }

    private suspend fun poll() {
        var failures = 0
        while (true) {
            failures = logged(state.refresh(), failures)
            withTimeoutOrNull(schedule.delayAfter(failures)) { wakeups.receive() }
        }
    }

    /** Logs counts and fixed categories only (ENG-C8) and returns the new count of consecutive failures. */
    private fun logged(
        listing: Outcome<SessionsSnapshot, RuntimeError>,
        failures: Int,
    ): Int =
        when (listing) {
            is Outcome.Ok -> {
                if (failures > 0) logger.info("Session listing recovered", "failures" to failures)
                logger.debug("Sessions listed", "count" to listing.value.sessions.size)
                0
            }

            is Outcome.Err -> {
                if (failures == 0) logger.warn("Session listing failed", "problem" to listing.error.toProblem())
                failures + 1
            }
        }

    private fun List<Project>.dirs(): Map<ProjectId, Path> = associate { it.id to it.dir }
}
