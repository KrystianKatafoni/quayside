package dev.quayside.sessions.api

import dev.quayside.core.domain.Outcome
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
 * What every sessions system must do when the user puts a session in a place of its project (FR-P9, AM-314, ADR 0033,
 * ENG-T2): the place shows in [Session.assignedDir] before the command returns, survives scans and the resume of a
 * disk session, works for any session of a project in any state and origin, and never changes the project of the
 * session. Runs on [FakeSessions] and on `sessionsModule` over fakes. The sessions run in `marketing`, the places
 * are `soft` or the root, so the place and the working directory always differ.
 */
public abstract class PlaceContract {
    /** A harness whose supervisor, projects and disk look like [world]. */
    public abstract fun TestScope.create(world: SessionsWorld): PlaceHarness

    private val sup = SessionId("aaaa0001")
    private val marketing: Path = P1_DIR.resolve("marketing")

    private fun uuid(n: Int): SessionId =
        SessionId("c0de${n.toString().padStart(UUID_PAD, '0')}-0000-4000-8000-000000000000")

    private fun live(n: Int): SessionId = SessionId(uuid(n).value.take(SHORT_ID))

    private fun file(
        n: Int,
        cwd: Path = marketing,
    ) = WorldPastSession(uuid(n), cwd, T0.minus(Duration.ofHours(n.toLong())), "title $n")

    private fun world(
        vararg files: WorldPastSession,
        sessions: List<WorldSession> = listOf(sessionIn(sup.value, marketing)),
    ) = SessionsWorld(
        listOf(
            WorldProject(P1, P1_DIR, subfolders = listOf(SOFT, "marketing")),
            WorldProject(NESTED, NESTED_DIR),
            WorldProject(P2, P2_DIR),
        ),
        sessions,
        pastSessions = files.toList(),
    )

    private suspend fun TestScope.settled(world: SessionsWorld): PlaceHarness = create(world).also { it.settle() }

    private val soft = PlaceAssignment.Subfolder(SOFT)

    @Test
    public fun `assign to a subfolder shows assignedDir before it returns`(): Unit =
        runTest {
            val harness = settled(world())

            harness.commands.assignPlace(sup, soft) shouldBe Outcome.Ok(Unit)

            // no settle: a command that returned Ok is already visible
            val session = harness.session(sup)
            session.assignedDir shouldBe P1_DIR.resolve(SOFT)
            session.placeDir shouldBe P1_DIR.resolve(SOFT)
            session.cwd shouldBe marketing
        }

    @Test
    public fun `assign to root shows the project dir`(): Unit =
        runTest {
            val harness = settled(world())

            harness.commands.assignPlace(sup, PlaceAssignment.ProjectRoot) shouldBe Outcome.Ok(Unit)

            harness.session(sup).assignedDir shouldBe P1_DIR
        }

    @Test
    public fun `automatic clears the place`(): Unit =
        runTest {
            val harness = settled(world())
            harness.commands.assignPlace(sup, soft)

            harness.commands.assignPlace(sup, null) shouldBe Outcome.Ok(Unit)

            harness.session(sup).assignedDir shouldBe null
            harness.session(sup).placeDir shouldBe marketing
        }

    @Test
    public fun `a later place replaces the earlier one`(): Unit =
        runTest {
            val harness = settled(world())
            harness.commands.assignPlace(sup, soft)

            harness.commands.assignPlace(sup, PlaceAssignment.Subfolder("marketing"))

            harness.session(sup).assignedDir shouldBe marketing
        }

    @Test
    public fun `assign applies to a session in any state`(): Unit =
        runTest {
            val states = listOf(SessionState.Done, SessionState.Failed, SessionState.Stopped, SessionState.Working)
            states.forEachIndexed { i, state ->
                val id = SessionId("bbbb000$i")
                val harness = settled(world(sessions = listOf(sessionIn(id.value, marketing, state))))

                harness.commands.assignPlace(id, soft) shouldBe Outcome.Ok(Unit)

                harness.session(id).assignedDir shouldBe P1_DIR.resolve(SOFT)
            }
        }

    @Test
    public fun `assign applies to a disk session`(): Unit =
        runTest {
            val harness = settled(world(file(1), sessions = emptyList()))

            harness.commands.assignPlace(uuid(1), soft) shouldBe Outcome.Ok(Unit)

            harness.session(uuid(1)).assignedDir shouldBe P1_DIR.resolve(SOFT)
            harness.session(uuid(1)).origin shouldBe SessionOrigin.DISK
        }

    @Test
    public fun `place of a disk session survives its resume`(): Unit =
        runTest {
            val harness = settled(world(file(1), sessions = emptyList()))
            harness.commands.assignPlace(uuid(1), soft)

            harness.commands.resume(uuid(1))

            harness.session(live(1)).assignedDir shouldBe P1_DIR.resolve(SOFT)
            harness.session(live(1)).cwd shouldBe marketing
            harness.settle()
            harness.session(live(1)).assignedDir shouldBe P1_DIR.resolve(SOFT)
        }

    @Test
    public fun `place is ignored after the subfolder leaves the group and works again when it comes back`(): Unit =
        runTest {
            val harness = settled(world())
            harness.commands.assignPlace(sup, soft)

            harness.setSubfolders(P1, listOf("marketing"))
            harness.settle()
            harness.session(sup).assignedDir shouldBe null

            harness.setSubfolders(P1, listOf(SOFT, "marketing"))
            harness.settle()
            harness.session(sup).assignedDir shouldBe P1_DIR.resolve(SOFT)
        }

    @Test
    public fun `place keeps the project of the session`(): Unit =
        runTest {
            val harness = settled(world())
            harness.session(sup).project shouldBe P1

            harness.commands.assignPlace(sup, soft)
            harness.commands.assignPlace(sup, PlaceAssignment.ProjectRoot)

            harness.session(sup).project shouldBe P1
        }

    @Test
    public fun `a session of a nested project cannot be put in a subfolder of the outer one`(): Unit =
        runTest {
            val harness = settled(world(sessions = listOf(sessionIn("nest0001", NESTED_DIR))))

            harness.commands.assignPlace(SessionId("nest0001"), soft) shouldBe
                Outcome.Err(SessionError.SubfolderUnavailable(NESTED, SubfolderProblem.NOT_IN_PROJECT))
            harness.session(SessionId("nest0001")).assignedDir shouldBe null
        }

    @Test
    public fun `place survives a new listing and a new scan`(): Unit =
        runTest {
            val harness = settled(world(file(1)))
            harness.commands.assignPlace(sup, soft)
            harness.commands.assignPlace(uuid(1), PlaceAssignment.ProjectRoot)

            harness.settle()
            harness.settle()

            harness.session(sup).assignedDir shouldBe P1_DIR.resolve(SOFT)
            harness.session(uuid(1)).assignedDir shouldBe P1_DIR
        }

    @Test
    public fun `subfolder in another case or outside the project is NOT_IN_PROJECT`(): Unit =
        runTest {
            val harness = settled(world())
            listOf("Soft", "../soft", "soft/api", "nope").forEach { name ->
                harness.commands.assignPlace(sup, PlaceAssignment.Subfolder(name)) shouldBe
                    Outcome.Err(SessionError.SubfolderUnavailable(P1, SubfolderProblem.NOT_IN_PROJECT))
            }

            harness.session(sup).assignedDir shouldBe null
        }

    @Test
    public fun `session outside every project is UnknownSession`(): Unit =
        runTest {
            val harness = settled(world(sessions = listOf(sessionIn("out00001", OUTSIDE_DIR))))
            val id = SessionId("out00001")

            harness.commands.assignPlace(id, soft) shouldBe Outcome.Err(SessionError.UnknownSession(id))
            harness.commands.assignPlace(id, null) shouldBe Outcome.Err(SessionError.UnknownSession(id))
        }

    @Test
    public fun `unknown session is UnknownSession`(): Unit =
        runTest {
            val harness = settled(world())
            val id = SessionId("gone0001")

            harness.commands.assignPlace(id, soft) shouldBe Outcome.Err(SessionError.UnknownSession(id))
        }

    @Test
    public fun `failed save is Runtime COMMAND_FAILED and changes nothing`(): Unit =
        runTest {
            val harness = settled(world())
            harness.commands.assignPlace(sup, soft)
            harness.failNextSettingsSave()

            harness.commands.assignPlace(sup, PlaceAssignment.ProjectRoot) shouldBe
                Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))

            harness.session(sup).assignedDir shouldBe P1_DIR.resolve(SOFT)
            harness.settle()
            harness.session(sup).assignedDir shouldBe P1_DIR.resolve(SOFT)
        }

    @Test
    public fun `failed save of automatic keeps the place`(): Unit =
        runTest {
            val harness = settled(world())
            harness.commands.assignPlace(sup, soft)
            harness.failNextSettingsSave()

            harness.commands.assignPlace(sup, null) shouldBe
                Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))

            harness.session(sup).assignedDir shouldBe P1_DIR.resolve(SOFT)
        }

    @Test
    public fun `assign emits no event and keeps the state`(): Unit =
        runTest {
            val harness = settled(world())
            val before = harness.session(sup)
            val events = recordEvents(harness.monitor)

            harness.commands.assignPlace(sup, soft)
            harness.settle()

            events.shouldBeEmpty()
            harness.session(sup).copy(assignedDir = null) shouldBe before
        }

    private companion object {
        const val SHORT_ID = 8
        const val UUID_PAD = 4
    }
}
