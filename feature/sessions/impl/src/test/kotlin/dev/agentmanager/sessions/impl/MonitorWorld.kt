@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.sessions.impl

import dev.agentmanager.agentruntime.api.FakeAgentRuntime
import dev.agentmanager.agentruntime.api.RuntimePhase
import dev.agentmanager.agentruntime.api.RuntimeSession
import dev.agentmanager.agentruntime.api.RuntimeWorld
import dev.agentmanager.agentruntime.api.WaitingFor
import dev.agentmanager.agentruntime.api.aRuntimeSession
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.core.testing.T0
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.Project
import dev.agentmanager.sessions.api.Session
import dev.agentmanager.sessions.api.SessionMonitor
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.impl.MonitorFixtures.P1
import dev.agentmanager.sessions.impl.MonitorFixtures.P1_DIR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What one test drives: the fakes behind the monitor and the monitor itself. */
internal class MonitorWorld(
    val fake: FakeAgentRuntime,
    val runtime: SwitchableRuntime,
    val projects: FakeProjectsApi,
    val logger: RecordingLogger,
    val monitor: SessionMonitor,
    private val scope: CoroutineScope,
) {
    fun session(id: String): Session =
        monitor.snapshot.value.sessions
            .single { it.id == SessionId(id) }

    fun stateOf(id: String): SessionState = session(id).state

    fun cancel() = scope.cancel()
}

/**
 * Builds the monitor over [fake] (or a runtime with [sessions]) and project P1 at [P1_DIR].
 * [pollInterval] null = the factory default.
 */
@Suppress("LongParameterList")
internal fun TestScope.monitorWorld(
    sessions: List<RuntimeSession> = emptyList(),
    fake: FakeAgentRuntime = FakeAgentRuntime(RuntimeWorld(sessions = sessions)),
    available: Boolean = true,
    listingTime: Duration = Duration.ZERO,
    pollInterval: Duration? = 2.seconds,
    scope: CoroutineScope = backgroundScope,
): MonitorWorld {
    val runtime = SwitchableRuntime(fake, now = { testScheduler.currentTime }, listingTime = listingTime)
    runtime.available(available)
    val projects = FakeProjectsApi(listOf(Project(P1, P1_DIR, "p1")))
    val logger = RecordingLogger()
    val clock = virtualClock()
    val monitor =
        if (pollInterval == null) {
            sessionsModule(runtime, projects, scope, clock, logger).monitor
        } else {
            sessionsModule(
                runtime,
                projects,
                scope,
                clock,
                logger,
                pollInterval,
                maxRetryInterval = 15.seconds,
            ).monitor
        }
    return MonitorWorld(fake, runtime, projects, logger, monitor, scope)
}

/** Shared data of the monitor tests: project P1 at [P1_DIR] is registered, P2 is not. */
internal object MonitorFixtures {
    const val S1 = "aaaa0001"
    const val S2 = "aaaa0002"
    const val S3 = "aaaa0003"
    const val NAME = "fix-login"
    val P1 = ProjectId("p1")
    val P1_DIR: Path = Path.of("/Users/test/projects/p1")
    val P2_DIR: Path = Path.of("/Users/test/projects/p2")
    val OUTSIDE_DIR: Path = Path.of("/Users/test/elsewhere")
    val AWAITING_INPUT = SessionState.AwaitingInput(question = null)
    val AWAITING_PERMISSION = SessionState.AwaitingPermission(tool = null)

    fun working(
        id: String,
        cwd: Path = P1_DIR,
    ): RuntimeSession = aRuntimeSession(id = SessionId(id), name = NAME, cwd = cwd, startedAt = T0)

    fun blocked(id: String): RuntimeSession =
        working(id).copy(phase = RuntimePhase.BLOCKED, waitingFor = WaitingFor.Input)

    fun millis(vararg values: Long): List<Long> = values.toList()
}
