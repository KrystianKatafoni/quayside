package dev.agentmanager.sessions.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.T0
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Behaviour every [SessionMonitor] must have (ENG-T2). Runs on [FakeSessions] and, from AM-403/603,
 * on the `sessions:impl` monitor. Each test builds a [SessionsHarness] from a [SessionsWorld] and
 * calls [SessionsHarness.settle] before it reads what the monitor saw.
 *
 * Blocked states are checked by kind only: polling cannot see the question or the tool, which come
 * from hooks (AM-603); details get their own contract test then.
 */
public abstract class SessionMonitorContract {
    /** Returns a harness whose supervisor and project catalogue look like [world]. */
    public abstract fun TestScope.create(world: SessionsWorld): SessionsHarness

    private val world =
        SessionsWorld(
            projects = projects(P1 to P1_DIR, P2 to P2_DIR),
            sessions = listOf(sessionIn("aaaa0001", P1_DIR), sessionIn("aaaa0002", P2_DIR.resolve("src"))),
        )
    private val first = SessionId("aaaa0001")

    private suspend fun TestScope.settled(world: SessionsWorld = this@SessionMonitorContract.world): SessionsHarness =
        create(world).also { it.settle() }

    @Test
    public fun `snapshot lists every session of the world after settling`(): Unit =
        runTest {
            val harness = settled()

            harness.monitor.snapshot.value.sessions
                .map { listOf(it.id, it.project, it.name, it.cwd, it.state, it.startedAt) } shouldBe
                listOf(
                    listOf(first, P1, "fix-login", P1_DIR, SessionState.Working, T0),
                    listOf(SessionId("aaaa0002"), P2, "fix-login", P2_DIR.resolve("src"), SessionState.Working, T0),
                )
        }

    @Test
    public fun `snapshot is fresh after settling`(): Unit =
        runTest {
            settled()
                .monitor.snapshot.value.status
                .shouldBeInstanceOf<SnapshotStatus.Fresh>()
        }

    @Test
    public fun `sessions are attributed to the deepest registered project`(): Unit =
        runTest {
            val nested =
                SessionsWorld(
                    projects = projects(P1 to P1_DIR, NESTED to NESTED_DIR),
                    sessions = listOf(sessionIn("aaaa0001", NESTED_DIR.resolve("src")), sessionIn("aaaa0002", P1_DIR)),
                )

            val harness = settled(nested)

            harness.session(SessionId("aaaa0001")).project shouldBe NESTED
            harness.session(SessionId("aaaa0002")).project shouldBe P1
        }

    @Test
    public fun `session outside every project has no project`(): Unit =
        runTest {
            val harness = settled(world.copy(sessions = listOf(sessionIn("aaaa0001", OUTSIDE_DIR))))

            harness.session(first).project shouldBe null
        }

    @Test
    public fun `sibling directory is not inside a project`(): Unit =
        runTest {
            val sibling = P1_DIR.resolveSibling(P1_DIR.fileName.toString() + "b")
            val harness = settled(world.copy(sessions = listOf(sessionIn("aaaa0001", sibling))))

            harness.session(first).project shouldBe null
        }

    @Test
    public fun `sessions are ordered by start time then id`(): Unit =
        runTest {
            val later = T0.plus(1.minutes.toJavaDuration())
            val sessions =
                listOf(
                    sessionIn("cccc0003", P1_DIR).copy(startedAt = later),
                    sessionIn("bbbb0002", P1_DIR).copy(startedAt = T0),
                    sessionIn("aaaa0001", P1_DIR).copy(startedAt = later),
                )

            settled(world.copy(sessions = sessions)).listedIds() shouldBe
                listOf(SessionId("bbbb0002"), SessionId("aaaa0001"), SessionId("cccc0003"))
        }

    @Test
    public fun `state change updates the snapshot`(): Unit =
        runTest {
            val harness = settled()

            harness.change(first, SessionState.Done)
            harness.settle()

            harness.stateOf(first) shouldBe SessionState.Done
        }

    @Test
    public fun `becoming blocked emits BecameBlocked`(): Unit =
        runTest {
            val event = eventAfterChange(SessionState.AwaitingInput(question = "Which file?"))

            event.shouldBeInstanceOf<SessionEvent.BecameBlocked>()
            event.session.state.shouldBeInstanceOf<SessionState.AwaitingInput>()
        }

    @Test
    public fun `permission request emits BecameBlocked`(): Unit =
        runTest {
            val event = eventAfterChange(SessionState.AwaitingPermission(tool = "Bash"))

            event.shouldBeInstanceOf<SessionEvent.BecameBlocked>()
            event.session.state.shouldBeInstanceOf<SessionState.AwaitingPermission>()
        }

    @Test
    public fun `session appearing while waiting emits BecameBlocked`(): Unit =
        runTest {
            val harness = settled()
            val events = recordEvents(harness.monitor)

            harness.appear(sessionIn("aaaa0009", P2_DIR, SessionState.AwaitingPermission(tool = "Bash")))
            harness.settle()

            events shouldHaveSize 1
            val event = events.single().shouldBeInstanceOf<SessionEvent.BecameBlocked>()
            event.session.id shouldBe SessionId("aaaa0009")
            event.session.state.shouldBeInstanceOf<SessionState.AwaitingPermission>()
        }

    @Test
    public fun `finishing a turn emits TurnFinished`(): Unit =
        runTest {
            val event = eventAfterChange(SessionState.Done)

            event.shouldBeInstanceOf<SessionEvent.TurnFinished>()
            event.session.state shouldBe SessionState.Done
        }

    @Test
    public fun `failure emits Failed`(): Unit =
        runTest {
            val event = eventAfterChange(SessionState.Failed)

            event.shouldBeInstanceOf<SessionEvent.Failed>()
            event.session.state shouldBe SessionState.Failed
        }

    @Test
    public fun `sessions present at start emit no events`(): Unit =
        runTest {
            val waiting =
                world.copy(
                    sessions =
                        listOf(
                            sessionIn("aaaa0001", P1_DIR, SessionState.AwaitingInput(question = null)),
                            sessionIn("aaaa0002", P1_DIR, SessionState.Done),
                            sessionIn("aaaa0003", P1_DIR, SessionState.Failed),
                        ),
                )
            val harness = create(waiting)
            val events = recordEvents(harness.monitor)

            harness.settle()
            harness.settle()

            harness.listedIds() shouldHaveSize 3
            events.shouldBeEmpty()
        }

    @Test
    public fun `unchanged state emits nothing`(): Unit =
        runTest {
            val harness = settled()
            val events = recordEvents(harness.monitor)

            harness.change(first, SessionState.Working)
            harness.settle()

            events.shouldBeEmpty()
        }

    @Test
    public fun `event comes after the snapshot shows the new state`(): Unit =
        runTest {
            val harness = settled()
            val seen =
                async(start = CoroutineStart.UNDISPATCHED) {
                    val event = harness.monitor.events.first()
                    event to harness.monitor.snapshot.value
                }

            harness.change(first, SessionState.Done)
            harness.settle()

            val (event, snapshotAtEvent) = seen.await()
            snapshotAtEvent.sessions shouldContain event.session
        }

    @Test
    public fun `unavailable runtime keeps the last sessions and marks them stale`(): Unit =
        runTest {
            val harness = settled()
            val before = harness.monitor.snapshot.value
            val freshAt = before.status.shouldBeInstanceOf<SnapshotStatus.Fresh>().at

            harness.runtimeAvailable(false)
            harness.settle()

            val after = harness.monitor.snapshot.value
            after.sessions shouldBe before.sessions
            after.status shouldBe SnapshotStatus.Stale(lastFreshAt = freshAt, problem = RuntimeProblem.NOT_INSTALLED)
        }

    @Test
    public fun `snapshot is fresh again when the runtime comes back`(): Unit =
        runTest {
            val harness = settled()
            harness.runtimeAvailable(false)
            harness.settle()

            harness.runtimeAvailable(true)
            harness.settle()

            harness.monitor.snapshot.value.status
                .shouldBeInstanceOf<SnapshotStatus.Fresh>()
        }

    @Test
    public fun `new session appears in the snapshot`(): Unit =
        runTest {
            val harness = settled()

            harness.appear(sessionIn("aaaa0009", P2_DIR, SessionState.AwaitingPermission(tool = "Bash")))
            harness.settle()

            harness.listedIds() shouldContainExactlyInAnyOrder
                listOf(first, SessionId("aaaa0002"), SessionId("aaaa0009"))
            harness.session(SessionId("aaaa0009")).project shouldBe P2
        }

    @Test
    public fun `recentOutput returns the output of the session`(): Unit =
        runTest {
            val harness = settled(world.copy(outputs = mapOf(first to "line 1\nline 2")))

            harness.monitor.recentOutput(first) shouldBe Outcome.Ok(RecentOutput("line 1\nline 2"))
        }

    @Test
    public fun `recentOutput of a session without output is empty`(): Unit =
        runTest {
            val harness = settled()

            val output = harness.monitor.recentOutput(first)

            output.shouldBeInstanceOf<Outcome.Ok<RecentOutput>>().value.isEmpty shouldBe true
        }

    @Test
    public fun `recentOutput of an unknown session fails with UnknownSession`(): Unit =
        runTest {
            val harness = settled()

            harness.monitor.recentOutput(SessionId("ffff0000")) shouldBe
                Outcome.Err(SessionError.UnknownSession(SessionId("ffff0000")))
        }

    @Test
    public fun `recentOutput fails with the runtime problem when the runtime is unavailable`(): Unit =
        runTest {
            val harness = settled(world.copy(outputs = mapOf(first to "text")))
            harness.runtimeAvailable(false)

            harness.monitor.recentOutput(first) shouldBe
                Outcome.Err(SessionError.Runtime(RuntimeProblem.NOT_INSTALLED))
        }

    @Test
    public fun `recentOutput changes neither the snapshot nor the events`(): Unit =
        runTest {
            val harness = settled(world.copy(outputs = mapOf(first to "text")))
            val events = recordEvents(harness.monitor)
            val before = harness.monitor.snapshot.value

            harness.monitor.recentOutput(first)
            harness.monitor.recentOutput(SessionId("ffff0000"))

            harness.monitor.snapshot.value shouldBe before
            events.shouldBeEmpty()
        }

    /** Moves the first session from Working to [state] and returns the single event it caused. */
    private suspend fun TestScope.eventAfterChange(state: SessionState): SessionEvent {
        val harness = settled()
        val events = recordEvents(harness.monitor)

        harness.change(first, state)
        harness.settle()

        events shouldHaveSize 1
        return events.single().also { it.session.id shouldBe first }
    }
}
