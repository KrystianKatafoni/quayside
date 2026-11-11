package dev.quayside.sessions.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration

/**
 * What every sessions system that lists session files must do (FR-P9, ADR 0032, ENG-T2): disk sessions in the
 * snapshot, the commands on them, the age and count limits. Runs on [FakeSessions] and on `sessionsModule` over a
 * `FakeSessionHistory`, so the fake used in window tests cannot drift from the implementation.
 *
 * Time: "now" is [T0]; files are modified before it.
 */
public abstract class PastSessionsContract {
    /** A harness whose supervisor, projects and disk look like [world]. */
    public abstract fun TestScope.create(world: SessionsWorld): PastSessionsHarness

    private fun uuid(n: Int): SessionId = SessionId("00000000-0000-4000-8000-${n.toString().padStart(12, '0')}")

    private fun file(
        n: Int,
        cwd: Path = P1_DIR,
        age: Duration = Duration.ofHours(n.toLong()),
        title: String? = "title $n",
    ): WorldPastSession = WorldPastSession(uuid(n), cwd, T0.minus(age), title)

    private fun world(
        vararg files: WorldPastSession,
        sessions: List<WorldSession> = emptyList(),
    ) = SessionsWorld(projects(P1 to P1_DIR, P2 to P2_DIR), sessions, pastSessions = files.toList())

    private suspend fun TestScope.settled(world: SessionsWorld): PastSessionsHarness =
        create(world).also { it.settle() }

    private fun PastSessionsHarness.diskIds(): List<SessionId> =
        monitor.snapshot.value.sessions
            .filter { it.origin == SessionOrigin.DISK }
            .map { it.id }

    private fun PastSessionsHarness.history(project: ProjectId): ProjectHistory? =
        monitor.snapshot.value.history[project]

    @Test
    public fun `a session file is listed as a stopped disk session of its project`(): Unit =
        runTest {
            val past =
                WorldPastSession(
                    uuid(1),
                    P1_DIR.resolve("src"),
                    modifiedAt = T0.minusSeconds(HOUR),
                    title = "Fix the login bug",
                    startedAt = T0.minusSeconds(2 * HOUR),
                    lastActivityAt = T0.minusSeconds(HOUR + MINUTES_30),
                )

            val harness = settled(world(past))

            harness.monitor.snapshot.value.sessions shouldBe
                listOf(
                    pastSession(
                        id = uuid(1).value,
                        project = P1,
                        cwd = P1_DIR.resolve("src"),
                        name = "Fix the login bug",
                        startedAt = T0.minusSeconds(2 * HOUR),
                        lastActivityAt = T0.minusSeconds(HOUR + MINUTES_30),
                        summary = "Fix the login bug",
                    ),
                )
        }

    @Test
    public fun `without a title the name is 8 characters of the id and the times are modifiedAt`(): Unit =
        runTest {
            val past = WorldPastSession(uuid(7), P1_DIR, modifiedAt = T0.minusSeconds(HOUR), title = null)

            val session =
                settled(world(past))
                    .monitor.snapshot.value.sessions
                    .single()

            session.name shouldBe "00000000"
            session.startedAt shouldBe T0.minusSeconds(HOUR)
            session.lastActivityAt shouldBe T0.minusSeconds(HOUR)
        }

    @Test
    public fun `a conversation the supervisor knows by its UUID is listed once`(): Unit =
        runTest {
            val known = WorldSession(SessionId("aaaa0001"), P1_DIR, conversationId = uuid(1).value)

            val harness = settled(world(file(1), file(2), sessions = listOf(known)))

            harness.diskIds() shouldBe listOf(uuid(2))
            harness.monitor.snapshot.value.sessions
                .map { it.id.value }
                .toSet() shouldBe setOf("aaaa0001", uuid(2).value)
        }

    @Test
    public fun `a conversation whose UUID starts with the short id of a supervisor session is listed once`(): Unit =
        runTest {
            val known = WorldSession(SessionId("00000000"), P1_DIR)

            settled(world(file(1), sessions = listOf(known))).diskIds().shouldBeEmpty()
        }

    @Test
    public fun `a session that started outside every project is not listed`(): Unit =
        runTest {
            val harness = settled(world(file(1, cwd = OUTSIDE_DIR)))

            harness.diskIds().shouldBeEmpty()
            harness.history(P1) shouldBe ProjectHistory(0, 0)
        }

    @Test
    public fun `the deepest project owns the session`(): Unit =
        runTest {
            val nested =
                SessionsWorld(projects(P1 to P1_DIR, NESTED to NESTED_DIR), pastSessions = listOf(file(1, NESTED_DIR)))

            val harness = settled(nested)

            harness.monitor.snapshot.value.sessions
                .single()
                .project shouldBe NESTED
            harness.history(NESTED) shouldBe ProjectHistory(1, 0)
            harness.history(P1) shouldBe ProjectHistory(0, 0)
        }

    @Test
    public fun `history has a key for every project, also with zeros`(): Unit =
        runTest {
            val harness = settled(world(file(1), file(2)))

            harness.monitor.snapshot.value.history shouldBe
                mapOf(P1 to ProjectHistory(2, 0), P2 to ProjectHistory(0, 0))
        }

    @Test
    public fun `disk sessions are not counted as active`(): Unit =
        runTest {
            val harness = settled(world(file(1), file(2)))

            harness.monitor.snapshot.value
                .countsFor(P1) shouldBe SessionCounts(working = 0, waiting = 0)
        }

    @Test
    public fun `a file that appears later is listed after settling`(): Unit =
        runTest {
            val harness = settled(world(file(1)))

            harness.givenPastSession(file(2))
            harness.settle()

            harness.diskIds().toSet() shouldBe setOf(uuid(1), uuid(2))
            harness.history(P1) shouldBe ProjectHistory(2, 0)
        }

    @Test
    public fun `the age limit leaves out older files and a wider limit brings them back`(): Unit =
        runTest {
            val recent = file(1, age = Duration.ofDays(1))
            val old = file(2, age = Duration.ofDays(10))
            val harness = settled(world(recent, old))
            harness.diskIds().toSet() shouldBe setOf(uuid(1), uuid(2))

            harness.settings.update { it.copy(maxAge = HistoryAge.DAYS_7) }
            harness.settle()
            harness.diskIds() shouldBe listOf(uuid(1))

            harness.settings.update { it.copy(maxAge = HistoryAge.UNLIMITED) }
            harness.settle()
            harness.diskIds().toSet() shouldBe setOf(uuid(1), uuid(2))
        }

    @Test
    public fun `the count limit keeps the newest and reports how many are older`(): Unit =
        runTest {
            val files = (1..LIMIT_50 + 2).map { file(it, age = Duration.ofMinutes(it.toLong())) }
            val harness = settled(world(*files.toTypedArray()))
            harness.settings.update { it.copy(maxPerProject = HistoryLimit.SESSIONS_50) }
            harness.settle()

            harness.history(P1) shouldBe ProjectHistory(shown = LIMIT_50, older = 2)
            harness.diskIds().toSet() shouldBe (1..LIMIT_50).map(::uuid).toSet()
        }

    @Test
    public fun `respawn of a disk session is UnknownSession`(): Unit =
        runTest {
            val harness = settled(world(file(1)))

            harness.commands.respawn(uuid(1)) shouldBe Outcome.Err(SessionError.UnknownSession(uuid(1)))
            harness.diskIds() shouldBe listOf(uuid(1))
        }

    @Test
    public fun `stop of a disk session is ok and changes nothing`(): Unit =
        runTest {
            val harness = settled(world(file(1)))
            val before = harness.monitor.snapshot.value.sessions

            harness.commands.stop(uuid(1)) shouldBe Outcome.Ok(Unit)
            harness.settle()

            harness.monitor.snapshot.value.sessions shouldBe before
        }

    @Test
    public fun `the output of a disk session is UnknownSession`(): Unit =
        runTest {
            val harness = settled(world(file(1)))

            harness.monitor.recentOutput(uuid(1)) shouldBe Outcome.Err(SessionError.UnknownSession(uuid(1)))
        }

    @Test
    public fun `removing a disk session hides it for good`(): Unit =
        runTest {
            val harness = settled(world(file(1), file(2)))

            harness.commands.remove(uuid(1)) shouldBe Outcome.Ok(Unit)
            harness.settle()
            harness.settle()

            harness.diskIds() shouldBe listOf(uuid(2))
            harness.settings.settings.value.hidden shouldBe setOf(uuid(1))
        }

    @Test
    public fun `a hidden session is not counted as older`(): Unit =
        runTest {
            val files = (1..LIMIT_50 + 1).map { file(it, age = Duration.ofMinutes(it.toLong())) }
            val harness = settled(world(*files.toTypedArray()))
            harness.settings.update { it.copy(maxPerProject = HistoryLimit.SESSIONS_50) }
            harness.settle()
            harness.history(P1) shouldBe ProjectHistory(LIMIT_50, 1)

            harness.commands.remove(uuid(1))
            harness.settle()

            harness.history(P1) shouldBe ProjectHistory(LIMIT_50, 0)
        }

    @Test
    public fun `a hide that could not be saved is an error and the session stays`(): Unit =
        runTest {
            val harness = settled(world(file(1)))
            harness.failNextSettingsSave()

            harness.commands.remove(uuid(1)) shouldBe Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))

            harness.settle()
            harness.diskIds() shouldBe listOf(uuid(1))
            harness.settings.settings.value.hidden shouldBe emptySet()
        }

    @Test
    public fun `removing a hidden session again is UnknownSession`(): Unit =
        runTest {
            val harness = settled(world(file(1)))
            harness.commands.remove(uuid(1))
            harness.settle()

            harness.commands.remove(uuid(1)) shouldBe Outcome.Err(SessionError.UnknownSession(uuid(1)))
        }

    @Test
    public fun `disk sessions emit no events`(): Unit =
        runTest {
            val harness = create(world(file(1)))
            val events = recordEvents(harness.monitor)
            harness.settle()

            harness.givenPastSession(file(2))
            harness.settle()
            harness.commands.remove(uuid(1))
            harness.settle()

            events.shouldBeEmpty()
        }

    private companion object {
        const val HOUR = 3_600L
        const val MINUTES_30 = 1_800L
        const val LIMIT_50 = 50
    }
}
