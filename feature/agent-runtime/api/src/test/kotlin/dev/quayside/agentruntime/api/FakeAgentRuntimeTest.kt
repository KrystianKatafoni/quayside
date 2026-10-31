package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class FakeAgentRuntimeTest {
    private val session = aRuntimeSession(id = SessionId("7c5dcf5d"))

    @Test
    fun `failNext affects only the next call`() =
        runTest {
            val runtime = FakeAgentRuntime(RuntimeWorld(sessions = listOf(session)))

            runtime.failNext(RuntimeError.Timeout)

            runtime.listSessions() shouldBe Outcome.Err(RuntimeError.Timeout)
            runtime.listSessions() shouldBe Outcome.Ok(listOf(session))
        }

    @Test
    fun `failNext applies to any suspend operation`() =
        runTest {
            val runtime = FakeAgentRuntime(RuntimeWorld(sessions = listOf(session)))
            val error = RuntimeError.CommandFailed(exitCode = 1, stderrTail = "supervisor not running")

            runtime.failNext(error)

            runtime.stop(session.id) shouldBe Outcome.Err(error)
            runtime
                .listSessions()
                .getOrNull()
                ?.single()
                ?.phase shouldBe RuntimePhase.WORKING
        }

    @Test
    fun `calls are recorded in order`() =
        runTest {
            val runtime = FakeAgentRuntime(RuntimeWorld(sessions = listOf(session)))

            runtime.listSessions()
            runtime.start(aStartSpec())
            runtime.stop(session.id)
            runtime.respawn(session.id)
            runtime.recentOutput(session.id)
            runtime.remove(session.id)

            runtime.calls shouldBe
                listOf(
                    "listSessions",
                    "start",
                    "stop 7c5dcf5d",
                    "respawn 7c5dcf5d",
                    "recentOutput 7c5dcf5d",
                    "remove 7c5dcf5d",
                )
        }

    @Test
    fun `setPhase changes the listed phase and waitingFor`() =
        runTest {
            val runtime = FakeAgentRuntime(RuntimeWorld(sessions = listOf(session)))

            runtime.setPhase(session.id, RuntimePhase.BLOCKED, WaitingFor.Permission)

            val listed =
                runtime
                    .listSessions()
                    .getOrNull()
                    ?.single()
                    .shouldNotBeNull()
            listed.phase shouldBe RuntimePhase.BLOCKED
            listed.waitingFor shouldBe WaitingFor.Permission
        }

    @Test
    fun `hasSession adds a session`() =
        runTest {
            val runtime = FakeAgentRuntime()

            runtime.hasSession(session)

            runtime.listSessions() shouldBe Outcome.Ok(listOf(session))
        }

    @Test
    fun `started sessions get deterministic ids and the clock time`() =
        runTest {
            val clock = TestClock()
            val runtime = FakeAgentRuntime(clock = clock)

            val firstId = runtime.start(aStartSpec(name = "one")).getOrNull()
            clock.advanceBy(5.minutes)
            val secondId = runtime.start(aStartSpec(name = "two")).getOrNull()

            firstId shouldBe SessionId("fake0001")
            secondId shouldBe SessionId("fake0002")
            val startedAt = runtime.listSessions().getOrNull()?.associate { it.id to it.startedAt }
            startedAt shouldBe mapOf(SessionId("fake0001") to T0, SessionId("fake0002") to T0.plusSeconds(300))
        }
}
