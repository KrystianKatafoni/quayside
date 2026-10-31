@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.impl.MonitorFixtures.NAME
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.P1_DIR
import dev.quayside.sessions.impl.MonitorFixtures.P2_DIR
import dev.quayside.sessions.impl.MonitorFixtures.S1
import dev.quayside.sessions.impl.MonitorFixtures.S2
import dev.quayside.sessions.impl.MonitorFixtures.blocked
import dev.quayside.sessions.impl.MonitorFixtures.millis
import dev.quayside.sessions.impl.MonitorFixtures.working
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Failed listings of `sessionsModule` (AM-403 plan, rules 3 and 4, docs/11 §7): stale snapshot,
 * backoff and logs without user data (ENG-C8). `pollInterval = 2 s`, `max = 15 s`.
 */
class PollingFailuresTest {
    @Test
    fun `failed listing marks the snapshot stale at once`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()
            val listed = world.monitor.snapshot.value.sessions

            advance(1.seconds)
            world.fake.failNext(RuntimeError.Timeout)
            advance(1.seconds)

            world.monitor.snapshot.value shouldBe
                SessionsSnapshot(listed, SnapshotStatus.Stale(lastFreshAt = T0, problem = RuntimeProblem.TIMEOUT))
        }

    @Test
    fun `failed first listing is stale without sessions`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)), available = false)

            runCurrent()

            world.monitor.snapshot.value shouldBe
                SessionsSnapshot(emptyList(), SnapshotStatus.Stale(lastFreshAt = null, RuntimeProblem.NOT_INSTALLED))
        }

    @Test
    fun `retries at the poll interval after one failure`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()
            advance(1.seconds)
            world.fake.failNext(RuntimeError.Timeout)

            advance(5.seconds)

            world.runtime.listedAtMillis shouldBe millis(0, 2_000, 4_000, 6_000)
            world.monitor.snapshot.value.status shouldBe SnapshotStatus.Fresh(atMillis(6_000))
        }

    @Test
    fun `backs off after repeated failures`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)), available = false)
            runCurrent()

            advance(60.seconds)

            // Waits after 1..6 failures: 2, 2, 4, 8, 15, 15 s.
            world.runtime.listedAtMillis shouldBe millis(0, 2_000, 4_000, 8_000, 16_000, 31_000, 46_000)
        }

    @Test
    fun `success resets the backoff`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)), available = false)
            runCurrent()
            advance(9.seconds)
            world.runtime.available(true)

            advance(11.seconds)

            world.runtime.listedAtMillis shouldBe millis(0, 2_000, 4_000, 8_000, 16_000, 18_000, 20_000)
            world.monitor.snapshot.value.status shouldBe SnapshotStatus.Fresh(atMillis(20_000))
        }

    @Test
    fun `project change does not reset the backoff`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)), available = false)
            runCurrent()
            advance(5.seconds)

            world.projects.remove(P1)
            advance(8.seconds)

            // Failures 1..3 at 0, 2, 4 s; the change lists at 5 s (failure 4), then the wait is 8 s.
            world.runtime.listedAtMillis shouldBe millis(0, 2_000, 4_000, 5_000, 13_000)
        }

    @Test
    fun `logs the stale transition once with the problem only`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()
            world.runtime.available(false)

            advance(20.seconds)

            val warnings = world.logger.entriesAt(LogLevel.WARN)
            warnings shouldHaveSize 1
            warnings.single().fields.keys shouldBe setOf("problem")
            warnings.single().fields["problem"].toString() shouldBe RuntimeProblem.NOT_INSTALLED.name
        }

    @Test
    fun `logs recovery once`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()
            world.runtime.available(false)
            advance(4.seconds)
            val beforeRecovery = world.logger.entries.size

            world.runtime.available(true)
            advance(10.seconds)

            world.logger.entries
                .drop(beforeRecovery)
                .filter { it.level == LogLevel.INFO } shouldHaveSize 1
        }

    @Test
    fun `logs never contain cwd or session names`() =
        runTest {
            val secret = working(S1, cwd = P1_DIR.resolve("secret-dir")).copy(name = "secret-name")
            val world = monitorWorld(sessions = listOf(secret, blocked(S2)))
            runCurrent()
            world.fake.setPhase(SessionId(S1), RuntimePhase.DONE)
            advance(2.seconds)
            world.runtime.available(false)
            advance(4.seconds)
            world.projects.add(P2_DIR)
            world.runtime.available(true)
            advance(10.seconds)

            world.logger.entries.shouldHaveAtLeastSize(1)
            world.logger.entries.forEach { entry ->
                val text = entry.toString()
                text shouldNotContain "secret"
                text shouldNotContain "/Users/test"
                text shouldNotContain NAME
            }
        }
}
