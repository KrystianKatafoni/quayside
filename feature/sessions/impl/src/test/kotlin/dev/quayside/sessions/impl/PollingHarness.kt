@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.agentruntime.api.WaitingFor
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionMonitor
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsHarness
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val fake =
        FakeAgentRuntime(RuntimeWorld(sessions = world.sessions.map { it.toRuntime() }, outputs = world.outputs))
    private val runtime = SwitchableRuntime(fake, now = { scope.testScheduler.currentTime })
    private val projects = FakeProjectsApi(world.projects.map { Project(it.id, it.dir, it.id.value) })

    private val api: SessionsApi =
        sessionsModule(
            runtime = runtime,
            projects = projects,
            scope = scope.backgroundScope,
            clock = scope.virtualClock(),
            logger = NoOpLogger,
            pollInterval = pollInterval,
        )

    override val monitor: SessionMonitor = api.monitor

    override val commands: SessionCommands = api.commands

    override suspend fun change(
        id: SessionId,
        state: SessionState,
    ) {
        val (phase, waitingFor) = state.toRuntimePhase()
        fake.setPhase(id, phase, waitingFor)
    }

    override suspend fun appear(session: WorldSession) {
        fake.hasSession(session.toRuntime())
    }

    override fun runtimeAvailable(available: Boolean) {
        runtime.available(available)
    }

    override fun failStop(id: SessionId) {
        runtime.failStop(id)
    }

    override suspend fun settle() {
        scope.advance(pollInterval)
    }
}

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
        conversationId = null,
        name = name,
        cwd = cwd,
        phase = phase,
        waitingFor = waitingFor,
        startedAt = startedAt,
        pid = null,
    )
}
