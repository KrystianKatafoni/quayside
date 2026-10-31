package dev.agentmanager.sessions.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.getOrNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Behaviour every [SessionCommands] must have (ENG-T2). Runs on [FakeSessions] and, from AM-404, on
 * the `sessions:impl` commands. Results of a successful command are read from
 * [SessionsHarness.monitor] without [SessionsHarness.settle]: they must be visible before the command
 * returns.
 */
public abstract class SessionCommandsContract {
    /** Returns a harness whose supervisor and project catalogue look like [world]. */
    public abstract fun TestScope.create(world: SessionsWorld): SessionsHarness

    private val registered = projects(P1 to P1_DIR, P2 to P2_DIR, NESTED to NESTED_DIR)
    private val unknownProject = ProjectId("unknown")
    private val unknownSession = SessionId("ffffffff")

    private suspend fun TestScope.settled(vararg sessions: WorldSession): SessionsHarness =
        create(SessionsWorld(projects = registered, sessions = sessions.toList())).also { it.settle() }

    @Test
    public fun `started session is working in its project before start returns`(): Unit =
        runTest {
            val harness = settled()

            val id =
                harness.commands
                    .start(P1, aNewSessionSpec(name = "fix-login"))
                    .getOrNull()
                    .shouldNotBeNull()

            val session = harness.session(id)
            listOf(session.project, session.cwd, session.name, session.state) shouldBe
                listOf(P1, P1_DIR, "fix-login", SessionState.Working)
        }

    @Test
    public fun `start in an unknown project returns UnknownProject`(): Unit =
        runTest {
            val harness = settled()

            harness.commands.start(unknownProject, aNewSessionSpec()) shouldBe
                Outcome.Err(SessionError.UnknownProject(unknownProject))
            harness.listedIds() shouldBe emptyList()
        }

    @Test
    public fun `stop makes the session stopped`(): Unit =
        runTest {
            val harness = settled(sessionIn(S1, P1_DIR, SessionState.AwaitingInput(question = null)))

            harness.commands.stop(SessionId(S1)) shouldBe Outcome.Ok(Unit)

            harness.stateOf(SessionId(S1)) shouldBe SessionState.Stopped
        }

    @Test
    public fun `stop of an ended session changes nothing`(): Unit =
        runTest {
            val harness = settled(sessionIn(S1, P1_DIR, SessionState.Done))

            harness.commands.stop(SessionId(S1)) shouldBe Outcome.Ok(Unit)

            harness.stateOf(SessionId(S1)) shouldBe SessionState.Done
        }

    @Test
    public fun `stop of an unknown session returns UnknownSession`(): Unit =
        runTest {
            settled().commands.stop(unknownSession) shouldBe Outcome.Err(SessionError.UnknownSession(unknownSession))
        }

    @Test
    public fun `stopAllIn stops only active sessions of the project and returns their number`(): Unit =
        runTest {
            val harness =
                settled(
                    sessionIn(S1, P1_DIR),
                    sessionIn(S2, P1_DIR.resolve("src"), SessionState.AwaitingPermission(tool = "Bash")),
                    sessionIn(S3, P1_DIR, SessionState.Done),
                    sessionIn(S4, P2_DIR),
                    sessionIn(S5, OUTSIDE_DIR),
                )

            harness.commands.stopAllIn(P1) shouldBe Outcome.Ok(2)

            listOf(S1, S2, S3, S4, S5).map { harness.stateOf(SessionId(it)) } shouldBe
                listOf(
                    SessionState.Stopped,
                    SessionState.Stopped,
                    SessionState.Done,
                    SessionState.Working,
                    SessionState.Working,
                )
        }

    @Test
    public fun `stopAllIn leaves sessions of a nested project running`(): Unit =
        runTest {
            val harness = settled(sessionIn(S1, P1_DIR), sessionIn(S2, NESTED_DIR.resolve("src")))

            harness.commands.stopAllIn(P1) shouldBe Outcome.Ok(1)

            harness.stateOf(SessionId(S1)) shouldBe SessionState.Stopped
            harness.stateOf(SessionId(S2)) shouldBe SessionState.Working
        }

    @Test
    public fun `stopAllIn without active sessions returns zero`(): Unit =
        runTest {
            val harness = settled(sessionIn(S1, P1_DIR, SessionState.Stopped), sessionIn(S2, P2_DIR))

            harness.commands.stopAllIn(P1) shouldBe Outcome.Ok(0)

            harness.stateOf(SessionId(S2)) shouldBe SessionState.Working
        }

    @Test
    public fun `stopAllIn of an unknown project returns UnknownProject`(): Unit =
        runTest {
            settled(sessionIn(S1, P1_DIR)).commands.stopAllIn(unknownProject) shouldBe
                Outcome.Err(SessionError.UnknownProject(unknownProject))
        }

    @Test
    public fun `stopAllIn tries every session and reports the failed ones`(): Unit =
        runTest {
            val harness = settled(sessionIn(S1, P1_DIR), sessionIn(S2, P1_DIR), sessionIn(S3, P1_DIR))
            harness.failStop(SessionId(S2))

            harness.commands.stopAllIn(P1) shouldBe
                Outcome.Err(SessionError.StopIncomplete(stopped = 2, failed = listOf(SessionId(S2))))

            listOf(S1, S2, S3).map { harness.stateOf(SessionId(it)) } shouldBe
                listOf(SessionState.Stopped, SessionState.Working, SessionState.Stopped)
        }

    @Test
    public fun `stopAllIn stops a session the snapshot does not show yet`(): Unit =
        runTest {
            val harness = settled(sessionIn(S1, P1_DIR))
            harness.appear(sessionIn(S2, P1_DIR))

            harness.commands.stopAllIn(P1) shouldBe Outcome.Ok(2)

            harness.stateOf(SessionId(S2)) shouldBe SessionState.Stopped
        }

    @Test
    public fun `respawn brings a stopped session back as working`(): Unit =
        runTest {
            val harness = settled(sessionIn(S1, P1_DIR, SessionState.Stopped))

            harness.commands.respawn(SessionId(S1)) shouldBe Outcome.Ok(Unit)

            harness.stateOf(SessionId(S1)) shouldBe SessionState.Working
        }

    @Test
    public fun `respawn of an active session returns SessionActive`(): Unit =
        runTest {
            val harness = settled(sessionIn(S1, P1_DIR))

            harness.commands.respawn(SessionId(S1)) shouldBe Outcome.Err(SessionError.SessionActive(SessionId(S1)))
            harness.stateOf(SessionId(S1)) shouldBe SessionState.Working
        }

    @Test
    public fun `remove makes the session disappear`(): Unit =
        runTest {
            val harness = settled(sessionIn(S1, P1_DIR, SessionState.Done), sessionIn(S2, P1_DIR))

            harness.commands.remove(SessionId(S1)) shouldBe Outcome.Ok(Unit)

            harness.listedIds() shouldBe listOf(SessionId(S2))
        }

    @Test
    public fun `remove of an active session returns SessionActive`(): Unit =
        runTest {
            val harness = settled(sessionIn(S1, P1_DIR, SessionState.AwaitingInput(question = null)))

            harness.commands.remove(SessionId(S1)) shouldBe Outcome.Err(SessionError.SessionActive(SessionId(S1)))
            harness.stateOf(SessionId(S1)) shouldBe SessionState.AwaitingInput(question = null)
        }

    @Test
    public fun `unavailable runtime returns Runtime error`(): Unit =
        runTest {
            val harness = settled(sessionIn(S1, P1_DIR), sessionIn(S2, P1_DIR, SessionState.Stopped))
            harness.runtimeAvailable(false)
            val notInstalled = Outcome.Err(SessionError.Runtime(RuntimeProblem.NOT_INSTALLED))

            harness.commands.start(P1, aNewSessionSpec()) shouldBe notInstalled
            harness.commands.stop(SessionId(S1)) shouldBe notInstalled
            harness.commands.stopAllIn(P1) shouldBe notInstalled
            harness.commands.respawn(SessionId(S2)) shouldBe notInstalled
            harness.commands.remove(SessionId(S2)) shouldBe notInstalled
            listOf(S1, S2).map { harness.stateOf(SessionId(it)) } shouldBe
                listOf(SessionState.Working, SessionState.Stopped)
            harness.listedIds() shouldBe listOf(SessionId(S1), SessionId(S2))
        }

    private companion object {
        const val S1 = "aaaa0001"
        const val S2 = "aaaa0002"
        const val S3 = "aaaa0003"
        const val S4 = "aaaa0004"
        const val S5 = "aaaa0005"
    }
}
