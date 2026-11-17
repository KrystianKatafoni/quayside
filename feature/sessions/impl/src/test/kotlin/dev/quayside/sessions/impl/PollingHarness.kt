@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.FakeAgentEventSource
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.agentruntime.api.WaitingFor
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionMonitor
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsHarness
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [SessionsHarness] over the polling monitor: the world becomes a [FakeProjectsApi] (display name =
 * id) and a [FakeAgentRuntime] behind a [SwitchableRuntime]; [settle] advances virtual time by one
 * poll interval. Both sides come from one `sessionsModule`; [failStop] makes the runtime fail that stop.
 */
internal class PollingHarness(
    private val scope: TestScope,
    world: SessionsWorld,
    private val pollInterval: Duration = 2.seconds,
) : SessionsHarness {
    // One supervisor per account (AM-1003): the default one and one for each account of the world.
    private val fakes =
        (listOf(AccountId.DEFAULT) + world.accounts).associateWith { account ->
            val sessions = world.sessions.filter { it.account == account }.map { it.toRuntime() }
            FakeAgentRuntime(RuntimeWorld(sessions = sessions, outputs = world.outputs, account = account))
        }
    private val runtimes =
        fakes.mapValues { (_, fake) -> SwitchableRuntime(fake, now = { scope.testScheduler.currentTime }) }
    private val accountOf = world.sessions.associate { it.id to it.account }.toMutableMap()
    private val runtime = runtimes.getValue(AccountId.DEFAULT)
    private val projects =
        FakeProjectsApi(
            world.projects.map { Project(it.id, it.dir, it.id.value, subfolderNames(*it.subfolders.toTypedArray())) },
        )
    private val missingDirs =
        world.projects
            .flatMap { project ->
                project.missing.map { project.dir.resolve(it) }
            }.toSet()

    // Hand-wired like `sessionsModule`, which has no seam for the disk check: `missing` of the world is
    // what the check reports as not a directory.
    // A world with extra accounts goes through the module itself: the hand-wiring below knows one runtime only.
    private val api: SessionsApi = if (world.accounts.isEmpty()) wired() else viaModule()

    private fun viaModule(): SessionsApi =
        sessionsModule(
            runtime,
            FakeAgentEventSource(),
            projects,
            scope.backgroundScope,
            scope.virtualClock(),
            NoOpLogger,
            pollInterval,
            MAX_RETRY,
            extraRuntimes = MutableStateFlow(runtimes.filterKeys { it != AccountId.DEFAULT }),
        )

    private fun wired(): SessionsApi {
        val clock = scope.virtualClock()
        val state = SessionsState(runtime, projects, clock, COMMAND_OVERLAY_TTL)
        val monitor =
            PollingSessionMonitor(
                projects = projects,
                state = state,
                schedule = PollSchedule(pollInterval, MAX_RETRY),
                logger = NoOpLogger,
                reader = RecentOutputReader(runtime, NoOpLogger),
                hooks = FakeAgentEventSource(),
            ).also { it.start(scope.backgroundScope) }
        val dirs = StartDirs(projects) { it !in missingDirs }
        return SessionsApi(monitor, RuntimeSessionCommands(runtime, dirs, state, clock, NoOpLogger))
    }

    override val monitor: SessionMonitor = api.monitor

    override val commands: SessionCommands = api.commands

    override suspend fun change(
        id: SessionId,
        state: SessionState,
    ) {
        val (phase, waitingFor) = state.toRuntimePhase()
        fakes.getValue(accountOf.getValue(id)).setPhase(id, phase, waitingFor)
    }

    override suspend fun appear(session: WorldSession) {
        accountOf[session.id] = session.account
        fakes.getValue(session.account).hasSession(session.toRuntime())
    }

    override fun runtimeAvailable(available: Boolean) {
        runtimes.values.forEach { it.available(available) }
    }

    override fun failStop(id: SessionId) {
        runtimes.getValue(accountOf.getValue(id)).failStop(id)
    }

    override suspend fun settle() {
        scope.advance(pollInterval)
    }
}

private val MAX_RETRY = 15.seconds

/** Inverse of [toSessionState] (AM-402 table): the supervisor phase and reason that show [this] state. */
internal fun SessionState.toRuntimePhase(): Pair<RuntimePhase, WaitingFor?> =
    when (this) {
        SessionState.Working -> RuntimePhase.WORKING to null
        is SessionState.AwaitingInput -> RuntimePhase.BLOCKED to WaitingFor.Input
        is SessionState.AwaitingPermission -> RuntimePhase.BLOCKED to WaitingFor.Permission
        SessionState.Done -> RuntimePhase.DONE to null
        SessionState.Failed -> RuntimePhase.FAILED to null
        SessionState.Stopped -> RuntimePhase.STOPPED to null
    }

internal fun WorldSession.toRuntime(): RuntimeSession {
    val (phase, waitingFor) = state.toRuntimePhase()
    return RuntimeSession(
        id = id,
        conversationId = conversationId?.let(::ConversationId),
        name = name,
        cwd = cwd,
        phase = phase,
        waitingFor = waitingFor,
        startedAt = startedAt,
        pid = null,
        account = account,
    )
}
