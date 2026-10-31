@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.sessions.impl

import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimePhase
import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.getOrNull
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.core.testing.T0
import dev.agentmanager.sessions.api.NewSessionSpec
import dev.agentmanager.sessions.api.RuntimeProblem
import dev.agentmanager.sessions.api.SessionError
import dev.agentmanager.sessions.api.SessionEvent
import dev.agentmanager.sessions.api.SessionState
import dev.agentmanager.sessions.api.SessionsSnapshot
import dev.agentmanager.sessions.api.SnapshotStatus
import dev.agentmanager.sessions.impl.MonitorFixtures.AWAITING_INPUT
import dev.agentmanager.sessions.impl.MonitorFixtures.OUTSIDE_DIR
import dev.agentmanager.sessions.impl.MonitorFixtures.P1
import dev.agentmanager.sessions.impl.MonitorFixtures.P1_DIR
import dev.agentmanager.sessions.impl.MonitorFixtures.S1
import dev.agentmanager.sessions.impl.MonitorFixtures.S2
import dev.agentmanager.sessions.impl.MonitorFixtures.S3
import dev.agentmanager.sessions.impl.MonitorFixtures.blocked
import dev.agentmanager.sessions.impl.MonitorFixtures.working
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.seconds

/**
 * The commands over the runtime (AM-404 plan, "Reguły komend"; FR-S3, FR-S4, FR-P4). Every test drives
 * [RuntimeSessionCommands] over one [SessionsState] without a polling loop; `listOnce` stands for one
 * listing of the loop. [LaggingRuntime] keeps showing the supervisor as it was before a command.
 */
class RuntimeSessionCommandsTest {
    @Test
    fun `start shows the session before the supervisor lists it`() =
        runTest {
            val world = commandsWorld()
            world.listOnce()
            world.runtime.lag = 2

            val id =
                world.commands
                    .start(P1, NewSessionSpec(prompt = "Fix the login bug", name = "fix-login"))
                    .getOrNull()
                    .shouldNotBeNull()

            val session = world.session(id)
            listOf(session.project, session.cwd, session.name, session.state) shouldBe
                listOf(P1, P1_DIR, "fix-login", SessionState.Working)
            world.runtime.commandCalls shouldBe listOf("start")

            advance(1.seconds)
            world.listOnce()
            world.session(id).state shouldBe SessionState.Working
        }

    @Test
    fun `start in an unknown project does not call the runtime`() =
        runTest {
            val world = commandsWorld()
            val unknown = ProjectId("unknown")

            world.commands.start(unknown, NewSessionSpec(prompt = "Fix the login bug")) shouldBe
                Outcome.Err(SessionError.UnknownProject(unknown))

            world.runtime.commandCalls.shouldBeEmpty()
        }

    @Test
    fun `stop shows the session stopped before the supervisor reports it`() =
        runTest {
            val world = commandsWorld(blocked(S1))
            world.listOnce()
            world.runtime.lag = 1

            world.commands.stop(SessionId(S1)) shouldBe Outcome.Ok(Unit)

            world.stateOf(S1) shouldBe SessionState.Stopped
            world.runtime.commandCalls shouldBe listOf("stop $S1")
        }

    @Test
    fun `a lagging listing does not bring a stopped session back`() =
        runTest {
            val world = commandsWorld(blocked(S1))
            world.listOnce()
            world.runtime.lag = 3
            val published = recordSnapshots(world)
            val events = recordEvents(world)

            world.commands.stop(SessionId(S1)) shouldBe Outcome.Ok(Unit)
            world.stateOf(S1) shouldBe SessionState.Stopped
            // Two more listings still show the session blocked, the next one shows it stopped.
            repeat(3) { listing ->
                advance(1.seconds)
                world.listOnce()
                withClue("listing ${listing + 1} after stop") { world.stateOf(S1) shouldBe SessionState.Stopped }
            }

            val states = published.mapNotNull { snapshot -> snapshot.sessions.singleOrNull()?.state }
            withClue("published states $states") {
                states.first() shouldBe AWAITING_INPUT
                states.drop(1).shouldNotBeEmpty()
                states.drop(1).all { it == SessionState.Stopped } shouldBe true
            }
            events.shouldBeEmpty()
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RuntimePhase::class, names = ["DONE", "FAILED", "STOPPED"])
    fun `stop of an inactive session does not call the runtime`(phase: RuntimePhase) =
        runTest {
            val world = commandsWorld(working(S1).copy(phase = phase))
            world.listOnce()
            val before = world.stateOf(S1)

            world.commands.stop(SessionId(S1)) shouldBe Outcome.Ok(Unit)

            world.runtime.commandCalls.shouldBeEmpty()
            world.stateOf(S1) shouldBe before
        }

    @Test
    fun `stop of a session the supervisor lost meanwhile returns UnknownSession`() =
        runTest {
            val world = commandsWorld(working(S1))
            world.listOnce()
            world.runtime.fail("stop $S1", RuntimeError.UnknownSession(SessionId(S1)), vanish = true)

            world.commands.stop(SessionId(S1)) shouldBe Outcome.Err(SessionError.UnknownSession(SessionId(S1)))
        }

    @Test
    fun `remove of an unknown session does not call the runtime`() =
        runTest {
            val world = commandsWorld(working(S1))
            world.listOnce()
            val unknown = SessionId("ffffffff")

            world.commands.remove(unknown) shouldBe Outcome.Err(SessionError.UnknownSession(unknown))
            world.commands.respawn(unknown) shouldBe Outcome.Err(SessionError.UnknownSession(unknown))
            world.commands.stop(unknown) shouldBe Outcome.Err(SessionError.UnknownSession(unknown))

            world.runtime.commandCalls.shouldBeEmpty()
        }

    @Test
    fun `respawn and remove of an active session do not call the runtime`() =
        runTest {
            val world = commandsWorld(working(S1), blocked(S2))
            world.listOnce()

            world.commands.respawn(SessionId(S1)) shouldBe Outcome.Err(SessionError.SessionActive(SessionId(S1)))
            world.commands.remove(SessionId(S2)) shouldBe Outcome.Err(SessionError.SessionActive(SessionId(S2)))

            world.runtime.commandCalls.shouldBeEmpty()
            world.stateOf(S2) shouldBe AWAITING_INPUT
        }

    @Test
    fun `respawn of a done session brings it back as working`() =
        runTest {
            val world = commandsWorld(working(S1).copy(phase = RuntimePhase.DONE))
            world.listOnce()
            world.runtime.lag = 2

            world.commands.respawn(SessionId(S1)) shouldBe Outcome.Ok(Unit)

            world.stateOf(S1) shouldBe SessionState.Working
            world.runtime.commandCalls shouldBe listOf("respawn $S1")
            advance(1.seconds)
            world.listOnce()
            world.stateOf(S1) shouldBe SessionState.Working
        }

    @Test
    fun `remove hides the session before the supervisor drops it`() =
        runTest {
            val world = commandsWorld(working(S1).copy(phase = RuntimePhase.DONE), working(S2))
            world.listOnce()
            world.runtime.lag = 2

            world.commands.remove(SessionId(S1)) shouldBe Outcome.Ok(Unit)

            world.listedIds() shouldBe listOf(SessionId(S2))
            world.runtime.commandCalls shouldBe listOf("remove $S1")
            advance(1.seconds)
            world.listOnce()
            world.listedIds() shouldBe listOf(SessionId(S2))
        }

    @Test
    fun `stopAllIn counts a session that vanished meanwhile as inactive`() =
        runTest {
            val world = commandsWorld(working(S1), working(S2))
            world.listOnce()
            world.runtime.fail("stop $S2", RuntimeError.UnknownSession(SessionId(S2)), vanish = true)

            world.commands.stopAllIn(P1) shouldBe Outcome.Ok(1)

            world.listedIds() shouldBe listOf(SessionId(S1))
            world.stateOf(S1) shouldBe SessionState.Stopped
        }

    @Test
    fun `stopAllIn publishes a fresh listing before it returns`() =
        runTest {
            val world = commandsWorld(working(S1))
            world.listOnce()
            advance(1.seconds)
            world.fake.hasSession(working(S2, cwd = OUTSIDE_DIR))
            world.fake.hasSession(working(S3))

            world.commands.stopAllIn(P1) shouldBe Outcome.Ok(2)

            world.snapshot.status shouldBe SnapshotStatus.Fresh(atMillis(1_000))
            world.snapshot.sessions.map { it.id to it.state } shouldContainExactlyInAnyOrder
                listOf(
                    SessionId(S1) to SessionState.Stopped,
                    SessionId(S2) to SessionState.Working,
                    SessionId(S3) to SessionState.Stopped,
                )
            world.runtime.commandCalls shouldContainExactlyInAnyOrder listOf("stop $S1", "stop $S3")
        }

    @Test
    fun `stopAllIn without active sessions still publishes a fresh listing`() =
        runTest {
            val world = commandsWorld(working(S1).copy(phase = RuntimePhase.DONE))
            world.listOnce()
            advance(1.seconds)
            world.fake.hasSession(working(S2, cwd = OUTSIDE_DIR))

            world.commands.stopAllIn(P1) shouldBe Outcome.Ok(0)

            world.snapshot.status shouldBe SnapshotStatus.Fresh(atMillis(1_000))
            world.listedIds() shouldBe listOf(SessionId(S1), SessionId(S2))
            world.runtime.commandCalls.shouldBeEmpty()
        }

    @Test
    fun `failed listing stops nothing`() =
        runTest {
            val world = commandsWorld(working(S1), working(S2).copy(phase = RuntimePhase.STOPPED))
            world.listOnce()
            world.runtime.failListings = RuntimeError.Timeout
            val timeout = Outcome.Err(SessionError.Runtime(RuntimeProblem.TIMEOUT))

            world.commands.stop(SessionId(S1)) shouldBe timeout
            world.commands.stopAllIn(P1) shouldBe timeout
            world.commands.respawn(SessionId(S2)) shouldBe timeout
            world.commands.remove(SessionId(S2)) shouldBe timeout

            world.runtime.commandCalls.shouldBeEmpty()
            world.snapshot.status shouldBe SnapshotStatus.Stale(lastFreshAt = T0, problem = RuntimeProblem.TIMEOUT)
            listOf(S1, S2).map { world.stateOf(it) } shouldBe listOf(SessionState.Working, SessionState.Stopped)
        }

    @Test
    fun `failed listing after a successful command returns Ok and marks the snapshot stale`() =
        runTest {
            val world = commandsWorld(working(S1))
            world.listOnce()
            world.runtime.failAfterCommand = RuntimeError.Timeout
            world.runtime.lag = 1

            world.commands.stop(SessionId(S1)) shouldBe Outcome.Ok(Unit)

            world.snapshot.status shouldBe SnapshotStatus.Stale(lastFreshAt = T0, problem = RuntimeProblem.TIMEOUT)

            // The next successful listing still lags behind; the kept expectation shows the stop.
            advance(1.seconds)
            world.listOnce()
            world.snapshot.status shouldBe SnapshotStatus.Fresh(atMillis(1_000))
            world.stateOf(S1) shouldBe SessionState.Stopped
        }

    @Test
    fun `stopAllIn reports the sessions whose stop failed`() =
        runTest {
            val world = commandsWorld(working(S1), working(S2), working(S3))
            world.listOnce()
            world.runtime.fail("stop $S2", RuntimeError.Timeout)

            world.commands.stopAllIn(P1) shouldBe
                Outcome.Err(SessionError.StopIncomplete(stopped = 2, failed = listOf(SessionId(S2))))

            listOf(S1, S2, S3).map { world.stateOf(it) } shouldBe
                listOf(SessionState.Stopped, SessionState.Working, SessionState.Stopped)
        }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("runtimeErrors")
    fun `runtime errors map to fixed problems`(
        error: RuntimeError,
        expected: SessionError,
    ) = runTest {
        val world = commandsWorld(working(S1))
        world.listOnce()
        world.runtime.fail("stop $S1", error)

        world.commands.stop(SessionId(S1)) shouldBe Outcome.Err(expected)
    }

    @Test
    fun `command logs carry ids and problems only`() =
        runTest {
            val secretDir = P1_DIR.resolve("secret-dir")
            val world =
                commandsWorld(
                    working(S1, cwd = secretDir).copy(name = "secret-name"),
                    working(S2, cwd = secretDir).copy(name = "secret-name"),
                )
            world.listOnce()
            world.runtime.fail("stop $S2", RuntimeError.CommandFailed(exitCode = 1, stderrTail = "secret-tail"))

            world.commands.start(P1, NewSessionSpec(prompt = "secret-prompt", name = "secret-name"))
            world.commands.stop(SessionId(S1))
            world.commands.stop(SessionId(S2))
            world.commands.stopAllIn(P1)

            world.logger.shouldCarryIdsAndProblemsOnly(stopped = S1, problem = RuntimeProblem.COMMAND_FAILED)
        }

    /** No prompt, name, cwd or stderr; an INFO entry names [stopped], a WARN entry carries [problem]. */
    private fun RecordingLogger.shouldCarryIdsAndProblemsOnly(
        stopped: String,
        problem: RuntimeProblem,
    ) {
        entries.shouldNotBeEmpty()
        entries.forEach { entry ->
            val text = entry.toString()
            text shouldNotContain "secret"
            text shouldNotContain "/Users/test"
        }
        withClue("an INFO entry names the stopped session") {
            entriesAt(LogLevel.INFO).any { entry -> entry.fields.values.any { it.toString() == stopped } } shouldBe true
        }
        withClue("a WARN entry carries the problem of the failed stop") {
            entriesAt(LogLevel.WARN).any { it.fields["problem"].toString() == problem.name } shouldBe true
        }
    }

    private fun TestScope.recordSnapshots(world: CommandsWorld): List<SessionsSnapshot> {
        val seen = mutableListOf<SessionsSnapshot>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { world.state.snapshot.collect { seen += it } }
        return seen
    }

    private fun TestScope.recordEvents(world: CommandsWorld): List<SessionEvent> {
        val seen = mutableListOf<SessionEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { world.state.events.collect { seen += it } }
        return seen
    }

    companion object {
        @JvmStatic
        fun runtimeErrors(): List<Arguments> =
            listOf(
                arguments(
                    RuntimeError.UnknownSession(SessionId(S1)),
                    SessionError.UnknownSession(SessionId(S1)),
                ),
                arguments(RuntimeError.NotInstalled, SessionError.Runtime(RuntimeProblem.NOT_INSTALLED)),
                arguments(
                    RuntimeError.UnsupportedVersion(found = "2.0.0", required = "2.1.139"),
                    SessionError.Runtime(RuntimeProblem.UNSUPPORTED_VERSION),
                ),
                arguments(RuntimeError.AgentViewDisabled, SessionError.Runtime(RuntimeProblem.AGENT_VIEW_DISABLED)),
                arguments(
                    RuntimeError.CommandFailed(exitCode = 2, stderrTail = "boom"),
                    SessionError.Runtime(RuntimeProblem.COMMAND_FAILED),
                ),
                arguments(RuntimeError.Unparseable("bad-json"), SessionError.Runtime(RuntimeProblem.UNPARSEABLE)),
                arguments(RuntimeError.Timeout, SessionError.Runtime(RuntimeProblem.TIMEOUT)),
            )
    }
}
