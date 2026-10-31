package dev.agentmanager.sessions.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.T0
import dev.agentmanager.core.testing.TestClock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class FakeSessionsTest {
    private val project = ProjectId("p1")
    private val dir = Path.of("/Users/test/projects/p1")
    private val existing = SessionId("aaaa0001")
    private val world =
        SessionsWorld(
            projects = listOf(WorldProject(project, dir)),
            sessions = listOf(WorldSession(existing, dir)),
        )

    @Test
    fun `failNext affects only the next command`() =
        runTest {
            val fake = FakeSessions(world)

            fake.failNext(SessionError.Runtime(RuntimeProblem.TIMEOUT))

            fake.stop(existing) shouldBe Outcome.Err(SessionError.Runtime(RuntimeProblem.TIMEOUT))
            fake.stop(existing) shouldBe Outcome.Ok(Unit)
        }

    @Test
    fun `failNext changes nothing`() =
        runTest {
            val fake = FakeSessions(world)
            val before = fake.snapshot.value

            fake.failNext(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))

            fake.stop(existing) shouldBe Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
            fake.snapshot.value shouldBe before
        }

    @Test
    fun `calls are recorded in order`() =
        runTest {
            val fake = FakeSessions(world)

            fake.start(project, aNewSessionSpec())
            fake.stop(existing)
            fake.stopAllIn(project)
            fake.respawn(existing)
            fake.remove(SessionId("00000001"))

            fake.calls shouldBe
                listOf("start p1", "stop aaaa0001", "stopAllIn p1", "respawn aaaa0001", "remove 00000001")
        }

    @Test
    fun `started sessions get sequential ids and are ordered by start time then id`() =
        runTest {
            val clock = TestClock()
            val fake = FakeSessions(world, clock)

            val first = fake.start(project, aNewSessionSpec())
            clock.advanceBy(1.minutes)
            val second = fake.start(project, aNewSessionSpec())

            listOf(first, second) shouldBe listOf(Outcome.Ok(SessionId("00000001")), Outcome.Ok(SessionId("00000002")))
            fake.snapshot.value.sessions
                .map { it.id to it.startedAt } shouldBe
                listOf(
                    // Ordered by start time, then id: 00000001 sorts before aaaa0001 at T0.
                    SessionId("00000001") to T0,
                    existing to T0,
                    SessionId("00000002") to T0.plus(1.minutes.toJavaDuration()),
                )
        }

    @Test
    fun `snapshot is fresh from the start`() {
        FakeSessions(world).snapshot.value.status shouldBe SnapshotStatus.Fresh(T0)
    }

    @Test
    fun `set output changes the next read`() =
        runTest {
            val fake = FakeSessions(world.copy(outputs = mapOf(existing to "old")))

            fake.setOutput(existing, "new")

            fake.recentOutput(existing) shouldBe Outcome.Ok(RecentOutput("new"))
        }

    @Test
    fun `fail next output fails once`() =
        runTest {
            val fake = FakeSessions(world.copy(outputs = mapOf(existing to "text")))
            fake.failNextOutput(SessionError.Runtime(RuntimeProblem.TIMEOUT))

            fake.recentOutput(existing) shouldBe Outcome.Err(SessionError.Runtime(RuntimeProblem.TIMEOUT))
            fake.recentOutput(existing) shouldBe Outcome.Ok(RecentOutput("text"))
        }

    @Test
    fun `output reads are not recorded as commands`() =
        runTest {
            val fake = FakeSessions(world)

            fake.recentOutput(existing)
            fake.recentOutput(SessionId("ffff0000"))

            fake.calls shouldBe emptyList()
            fake.outputReads shouldBe listOf(existing, SessionId("ffff0000"))
        }
}
