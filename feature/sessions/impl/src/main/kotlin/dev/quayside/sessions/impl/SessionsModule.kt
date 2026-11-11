package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentEventSource
import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.SessionHistory
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import dev.quayside.sessions.api.SessionMonitor
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.summaries.api.SummariesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
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
 * @param summaries gives each session a short summary of its last message after a `Stop` hook (FR-S7, AM-604); null
 * means no summaries. With a refiner, the model summary replaces the quick one in the background (ADR 0019): one
 * model call at a time, a newer `Stop` of a conversation cancels the older refinement, and the result is published
 * without an event. A refinement error logs one warning with a fixed category.
 * @param claudeHome the Claude Code data directory (`~/.claude`): the tail of the transcript of each conversation gives
 * the model and the permission mode shown in the details (FR-S8, AM-818); null = they stay unknown.
 * @param io dispatcher of the file reads of every listing (the transcripts and the `HEAD` of each session directory,
 * which gives the branch), usually `AppDispatchers.io`; null = no file is read and model, mode and branch stay
 * unknown (tests that must not leave the test scheduler). A missing file is an unknown value, never an error.
 * It also runs the disk check of a start in a subfolder (`NOFOLLOW_LINKS`, AM-308); with null the check is skipped
 * and a missing directory is reported by the runtime as `COMMAND_FAILED`.
 * @param history the session files of Claude Code (ADR 0032); with [historySettings] the snapshot also lists them as
 * disk sessions (FR-P9), scanned at start, after a change of the project dirs or of the settings, and every 60 s;
 * null = no disk sessions and an empty [dev.quayside.sessions.api.SessionsSnapshot.history].
 * @param historySettings age and count limit, the hidden sessions and the places picked by the user (FR-P9, AM-314),
 * shared by the scan and by `assignPlace`; null together with [history] = defaults, kept in memory.
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
    summaries: SummariesApi? = null,
    claudeHome: Path? = null,
    io: CoroutineDispatcher? = null,
    history: SessionHistory? = null,
    historySettings: SessionHistorySettingsRepo? = null,
): SessionsApi {
    val settings = historySettings ?: InMemoryHistorySettings()
    val scan = history?.let { HistoryScan(it, settings, projects, clock, logger) }
    val refinement = summaries?.refiner?.let { SummaryRefinement(it, scope, logger) }
    val state =
        SessionsState(
            runtime,
            projects,
            clock,
            COMMAND_OVERLAY_TTL,
            summaries = TurnSummaries(summaries?.summarizer, refinement),
            metas = io?.let { FileSessionMetaSource(claudeHome, it) } ?: NoSessionMetas,
            disk = scan,
            settings = settings,
        )
    val monitor =
        PollingSessionMonitor(
            projects = projects,
            state = state,
            schedule = PollSchedule(pollInterval, maxRetryInterval),
            logger = logger,
            reader = RecentOutputReader(runtime, logger),
            hooks = hooks,
        ).also { it.start(scope) }
    scan?.start(scope, state::republishDisk)
    val commands = RuntimeSessionCommands(runtime, StartDirs(projects, directoryCheck(io)), state, clock, logger)
    return SessionsApi(monitor = monitor, commands = commands)
}

/** The disk check of a start in a subfolder (`NOFOLLOW_LINKS`, AM-308); without [io] every directory passes. */
private fun directoryCheck(io: CoroutineDispatcher?): suspend (Path) -> Boolean =
    if (io == null) {
        { true }
    } else {
        { dir -> withContext(io) { Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) } }
    }
