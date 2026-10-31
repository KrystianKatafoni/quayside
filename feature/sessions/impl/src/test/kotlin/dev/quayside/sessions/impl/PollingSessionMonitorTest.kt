@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.agentruntime.api.WaitingFor
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import dev.quayside.sessions.impl.MonitorFixtures.AWAITING_INPUT
import dev.quayside.sessions.impl.MonitorFixtures.AWAITING_PERMISSION
import dev.quayside.sessions.impl.MonitorFixtures.NAME
import dev.quayside.sessions.impl.MonitorFixtures.OUTSIDE_DIR
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.P1_DIR
import dev.quayside.sessions.impl.MonitorFixtures.S1
import dev.quayside.sessions.impl.MonitorFixtures.S2
import dev.quayside.sessions.impl.MonitorFixtures.S3
import dev.quayside.sessions.impl.MonitorFixtures.blocked
import dev.quayside.sessions.impl.MonitorFixtures.millis
import dev.quayside.sessions.impl.MonitorFixtures.working
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The polling loop of `sessionsModule` (AM-403 plan, "Reguły pętli"; FR-S1, NFR-2, NFR-3): first
 * listing, rhythm, restart and cancellation. Every test runs the loop in `backgroundScope` on virtual
 * time; `pollInterval = 2 s`, `max = 15 s` unless the test is about the defaults.
 */
class PollingSessionMonitorTest {
    @Test
    fun `snapshot is loading until the first listing`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)), listingTime = 1.seconds)

            world.monitor.snapshot.value shouldBe SessionsSnapshot.Initial
            advance(999.milliseconds)
            world.monitor.snapshot.value shouldBe SessionsSnapshot.Initial

            advance(1.milliseconds)
            world.monitor.snapshot.value.status shouldBe SnapshotStatus.Fresh(atMillis(1_000))
        }

    @Test
    fun `first listing runs at once`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1), blocked(S2)))

            runCurrent()

            world.runtime.listedAtMillis shouldBe listOf(0L)
            world.monitor.snapshot.value shouldBe
                SessionsSnapshot(
                    sessions =
                        listOf(
                            aSession(id = S1, project = P1, cwd = P1_DIR, name = NAME),
                            aSession(id = S2, project = P1, cwd = P1_DIR, name = NAME, state = AWAITING_INPUT),
                        ),
                    status = SnapshotStatus.Fresh(T0),
                )
        }

    @Test
    fun `lists every session, not only those under projects`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1), working(S2, cwd = OUTSIDE_DIR)))

            runCurrent()

            world.monitor.snapshot.value.sessions
                .map { it.id to it.project } shouldBe listOf(SessionId(S1) to P1, SessionId(S2) to null)
        }

    @Test
    fun `lists again every poll interval`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()

            advance(1.seconds)
            world.fake.setPhase(SessionId(S1), RuntimePhase.DONE)
            advance(999.milliseconds)
            world.stateOf(S1) shouldBe SessionState.Working

            advance(1.milliseconds)
            world.stateOf(S1) shouldBe SessionState.Done
            advance(4.seconds)
            world.runtime.listedAtMillis shouldBe millis(0, 2_000, 4_000, 6_000)
            world.monitor.snapshot.value.status shouldBe SnapshotStatus.Fresh(atMillis(6_000))
        }

    @Test
    fun `waits the poll interval after the previous listing ends`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)), listingTime = 500.milliseconds)
            runCurrent()

            advance(5.seconds)

            world.runtime.listedAtMillis shouldBe millis(0, 2_500, 5_000)
        }

    @Test
    fun `refresh interval stays within three seconds`() =
        runTest {
            // NFR-2 with the default poll interval of the factory.
            val world = monitorWorld(sessions = listOf(working(S1)), pollInterval = null)
            runCurrent()

            advance(9.seconds)

            val listed = world.runtime.listedAtMillis
            listed shouldHaveAtLeastSize 4
            listed.first() shouldBe 0L
            withClue("listings at $listed ms") {
                (listed.zipWithNext { a, b -> b - a } + (9_000 - listed.last())).all { it <= 3_000 } shouldBe true
            }
        }

    @Test
    fun `restart rebuilds the state from the listing without events`() =
        runTest {
            val fake = FakeAgentRuntime(RuntimeWorld(sessions = listOf(working(S1), working(S2), working(S3))))
            val before = monitorWorld(fake = fake, scope = childScope())
            runCurrent()
            fake.setPhase(SessionId(S1), RuntimePhase.BLOCKED, WaitingFor.Permission)
            fake.setPhase(SessionId(S2), RuntimePhase.BLOCKED, WaitingFor.Input)
            fake.setPhase(SessionId(S3), RuntimePhase.DONE)
            advance(2.seconds)
            before.cancel()

            val after = monitorWorld(fake = fake)
            val events = record(after.monitor.events)
            runCurrent()
            advance(2.seconds)

            events.shouldBeEmpty()
            after.monitor.snapshot.value.sessions shouldBe
                listOf(
                    aSession(id = S1, project = P1, cwd = P1_DIR, name = NAME, state = AWAITING_PERMISSION),
                    aSession(id = S2, project = P1, cwd = P1_DIR, name = NAME, state = AWAITING_INPUT),
                    aSession(id = S3, project = P1, cwd = P1_DIR, name = NAME, state = SessionState.Done),
                )
        }

    @Test
    fun `cancelling the scope stops polling and keeps the last snapshot`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)), scope = childScope())
            runCurrent()
            advance(2.seconds)
            val last = world.monitor.snapshot.value

            advance(1.seconds)
            world.cancel()
            advance(30.seconds)

            world.runtime.listedAtMillis shouldBe millis(0, 2_000)
            world.monitor.snapshot.value shouldBe last
            last.status shouldBe SnapshotStatus.Fresh(atMillis(2_000))
        }

    @Test
    fun `cancelling the scope stops the project watcher`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)), scope = childScope())
            runCurrent()

            world.cancel()
            world.projects.remove(P1)
            advance(5.seconds)

            world.runtime.listedAtMillis shouldBe millis(0)
        }

    @Test
    fun `events follow transitions seen by polling`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()
            val events = record(world.monitor.events)

            world.fake.setPhase(SessionId(S1), RuntimePhase.BLOCKED, WaitingFor.Permission)
            advance(2.seconds)
            world.fake.setPhase(SessionId(S1), RuntimePhase.DONE)
            advance(2.seconds)

            events.map { it::class } shouldBe
                listOf(SessionEvent.BecameBlocked::class, SessionEvent.TurnFinished::class)
        }
}
