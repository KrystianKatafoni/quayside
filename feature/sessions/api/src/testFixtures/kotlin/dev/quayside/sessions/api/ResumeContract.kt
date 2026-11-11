package dev.quayside.sessions.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration

/**
 * What every sessions system that lists session files must do when a disk session is resumed (FR-P9, AM-313,
 * ENG-T2): the live session replaces the disk row before `resume` returns, a copy leaves the original, and every
 * check fails before Claude Code is called. Runs on [FakeSessions] and on `sessionsModule` over fakes, so the fake
 * that window tests use cannot drift from the implementation.
 *
 * The missing start directory is not here: `sessionsModule` checks it through an injected disk check that this
 * contract cannot script; `sessions:impl` and `FakeSessions` have their own tests for it.
 */
public abstract class ResumeContract {
    /** A harness whose supervisor, projects and disk look like [world]. */
    public abstract fun TestScope.create(world: SessionsWorld): ResumeHarness

    // The first 8 characters differ per conversation (the supervisor's short id is that prefix, docs/compat.md R5)
    // and are not an id the fake hands out to a copy (`00000001`).
    private fun uuid(n: Int): SessionId =
        SessionId("c0de${n.toString().padStart(SHORT_ID - 4, '0')}-0000-4000-8000-000000000000")

    /** What the supervisor calls the live session of conversation [n]: the first 8 characters of its UUID. */
    private fun live(n: Int): SessionId = SessionId(uuid(n).value.take(SHORT_ID))

    private fun file(
        n: Int,
        cwd: Path = P1_DIR,
    ): WorldPastSession = WorldPastSession(uuid(n), cwd, T0.minus(Duration.ofHours(n.toLong())), "title $n")

    private fun world(
        vararg files: WorldPastSession,
        sessions: List<WorldSession> = emptyList(),
    ) = SessionsWorld(projects(P1 to P1_DIR, NESTED to NESTED_DIR), sessions, pastSessions = files.toList())

    private suspend fun TestScope.settled(world: SessionsWorld): ResumeHarness = create(world).also { it.settle() }

    private fun ResumeHarness.diskIds(): List<SessionId> =
        monitor.snapshot.value.sessions
            .filter { it.origin == SessionOrigin.DISK }
            .map { it.id }

    private fun ResumeHarness.supervisorIds(): List<SessionId> =
        monitor.snapshot.value.sessions
            .filter { it.origin == SessionOrigin.SUPERVISOR }
            .map { it.id }

    @Test
    public fun `resume returns the short id of the conversation and no copy`(): Unit =
        runTest {
            val harness = settled(world(file(1)))

            harness.commands.resume(uuid(1)) shouldBe Outcome.Ok(ResumedSession(live(1), copy = false))
        }

    @Test
    public fun `the live session waits for input in the snapshot before resume returns`(): Unit =
        runTest {
            val harness = settled(world(file(1)))

            harness.commands.resume(uuid(1))

            // no settle: a command that returned Ok is already visible
            val session = harness.session(live(1))
            session.state shouldBe SessionState.AwaitingInput(null)
            session.origin shouldBe SessionOrigin.SUPERVISOR
            session.cwd shouldBe P1_DIR
            // the name is not pinned: the supervisor names the job once it lists it (the overlay uses the disk name)
        }

    @Test
    public fun `the disk row is gone when resume returns`(): Unit =
        runTest {
            val harness = settled(world(file(1), file(2)))

            harness.commands.resume(uuid(1))

            harness.diskIds() shouldBe listOf(uuid(2))
        }

    @Test
    public fun `the live session stays alone after the next scans`(): Unit =
        runTest {
            val harness = settled(world(file(1)))
            harness.commands.resume(uuid(1))

            harness.settle()
            harness.settle()

            harness.diskIds().shouldBeEmpty()
            harness.supervisorIds() shouldBe listOf(live(1))
        }

    @Test
    public fun `the resumed session keeps its project and place`(): Unit =
        runTest {
            val harness = settled(world(file(1, cwd = NESTED_DIR)))

            harness.commands.resume(uuid(1))

            val session = harness.session(live(1))
            session.project shouldBe NESTED
            session.cwd shouldBe NESTED_DIR
        }

    @Test
    public fun `the resumed session emits BecameBlocked once`(): Unit =
        runTest {
            val harness = create(world(file(1)))
            harness.settle()
            val events = recordEvents(harness.monitor)

            harness.commands.resume(uuid(1))
            harness.settle()
            harness.settle()

            events.map { it::class } shouldBe listOf(SessionEvent.BecameBlocked::class)
            (events.single() as SessionEvent.BecameBlocked).session.id shouldBe live(1)
        }

    @Test
    public fun `a conversation open elsewhere runs as a copy and the disk row stays`(): Unit =
        runTest {
            val harness = settled(world(file(1)))
            harness.openElsewhere(uuid(1))

            val resumed = (harness.commands.resume(uuid(1)) as Outcome.Ok).value
            harness.settle()

            resumed.copy shouldBe true
            resumed.id shouldNotBe live(1)
            harness.supervisorIds() shouldBe listOf(resumed.id)
            harness.diskIds() shouldBe listOf(uuid(1))
            harness.session(resumed.id).state shouldBe SessionState.AwaitingInput(null)
        }

    @Test
    public fun `a file gone since the scan is FILE_MISSING and nothing starts`(): Unit =
        runTest {
            val harness = settled(world(file(1)))
            harness.removeSessionFile(uuid(1))
            // the row is still listed until the next scan: the check must look at the disk, not at the snapshot
            harness.diskIds() shouldBe listOf(uuid(1))

            val result = harness.commands.resume(uuid(1))

            result shouldBe Outcome.Err(SessionError.ResumeUnavailable(uuid(1), ResumeProblem.FILE_MISSING))
            harness.supervisorIds().shouldBeEmpty()
        }

    @Test
    public fun `a supervisor session is UnknownSession`(): Unit =
        runTest {
            val harness = settled(world(file(1), sessions = listOf(sessionIn("aaaa0001", P1_DIR))))

            harness.commands.resume(SessionId("aaaa0001")) shouldBe
                Outcome.Err(SessionError.UnknownSession(SessionId("aaaa0001")))
            harness.supervisorIds() shouldBe listOf(SessionId("aaaa0001"))
        }

    @Test
    public fun `an unknown id is UnknownSession`(): Unit =
        runTest {
            val harness = settled(world(file(1)))

            harness.commands.resume(uuid(9)) shouldBe Outcome.Err(SessionError.UnknownSession(uuid(9)))
        }

    @Test
    public fun `a hidden disk session is UnknownSession`(): Unit =
        runTest {
            val harness = settled(world(file(1)))
            harness.commands.remove(uuid(1))
            harness.settle()

            harness.commands.resume(uuid(1)) shouldBe Outcome.Err(SessionError.UnknownSession(uuid(1)))
            harness.supervisorIds().shouldBeEmpty()
        }

    @Test
    public fun `a second resume of the same session is UnknownSession`(): Unit =
        runTest {
            val harness = settled(world(file(1)))
            harness.commands.resume(uuid(1))

            harness.commands.resume(uuid(1)) shouldBe Outcome.Err(SessionError.UnknownSession(uuid(1)))
            harness.supervisorIds() shouldBe listOf(live(1))
        }

    @Test
    public fun `a missing Claude Code is a runtime error and the disk row stays`(): Unit =
        runTest {
            val harness = settled(world(file(1)))
            harness.runtimeAvailable(false)

            harness.commands.resume(uuid(1)) shouldBe
                Outcome.Err(SessionError.Runtime(RuntimeProblem.NOT_INSTALLED))

            harness.diskIds() shouldContain uuid(1)
            harness.listedIds() shouldNotContain live(1)
        }

    private companion object {
        const val SHORT_ID = 8
    }
}
