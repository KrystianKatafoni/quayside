package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentEventSource
import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionMonitor
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Starts watching the supervisor in [scope] and returns both sides of sessions over one state (FR-S1,
 * FR-S3, FR-S4, NFR-2, NFR-3).
 *
 * The first listing runs at once, then one every [pollInterval] after the previous one ends; a change of
 * the project dirs in [projects] lists again at once. After repeated failures the wait grows up to
 * [maxRetryInterval] while the snapshot stays [SnapshotStatus.Stale]. Cancelling [scope] stops polling; the
 * last snapshot stays readable. Not suspending: the snapshot is [SessionsSnapshot.Initial] until the first
 * listing. The long parameter list is deliberate: `impl` may expose only factories.
 *
 * Every successful command is in [SessionMonitor.snapshot] before it returns, from a real listing with
 * the command's effect laid over it until the supervisor reports it (at most [COMMAND_OVERLAY_TTL]).
 * Commands never throw.
 *
 * Hook events (ADR 0004, AM-603) change the snapshot between two listings: a session that [hooks] report as
 * waiting, finished or failed shows that state at once, with the question or tool, and the next listing confirms
 * or corrects it. A listing that still says `WORKING` is overruled for at most [HOOK_LEAD].
 *
 * @param hooks hook events from `agent-runtime`; a hot stream without replay.
 * @param scope owner of the loop, e.g. the app scope with a SupervisorJob; tests pass `backgroundScope`.
 * @param clock time of each listing ([SnapshotStatus.Fresh.at], `lastActivityAt`).
 * @param logger gets session ids, counts and [RuntimeProblem] only, never cwd, names or prompts (ENG-C8).
 * @throws IllegalArgumentException if [pollInterval] is not positive or [maxRetryInterval] is shorter.
 */
@Suppress("LongParameterList")
public fun sessionsModule(
    runtime: AgentRuntime,
    hooks: AgentEventSource,
    projects: ProjectsApi,
    scope: CoroutineScope,
    clock: Clock,
    logger: Logger,
    pollInterval: Duration = 2.seconds,
    maxRetryInterval: Duration = 15.seconds,
): SessionsApi {
    val state = SessionsState(runtime, projects, clock, COMMAND_OVERLAY_TTL)
    val monitor =
        PollingSessionMonitor(
            projects = projects,
            state = state,
            schedule = PollSchedule(pollInterval, maxRetryInterval),
            logger = logger,
            reader = RecentOutputReader(runtime, logger),
            hooks = hooks,
        ).also { it.start(scope) }
    val commands = RuntimeSessionCommands(runtime, projects, state, clock, logger)
    return SessionsApi(monitor = monitor, commands = commands)
}
