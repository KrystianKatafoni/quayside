@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.core.domain.getOrNull
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.P2_DIR
import dev.quayside.sessions.impl.MonitorFixtures.S1
import dev.quayside.sessions.impl.MonitorFixtures.millis
import dev.quayside.sessions.impl.MonitorFixtures.working
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Project changes seen by `sessionsModule` (AM-403 plan, rules 1–3): a changed project map lists
 * again at once, a rename does not. `pollInterval = 2 s`, `max = 15 s`.
 */
class PollingProjectsTest {
    @Test
    fun `adding a project lists again at once and attributes its sessions`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1, cwd = P2_DIR.resolve("src"))))
            runCurrent()
            world.session(S1).project shouldBe null

            advance(500.milliseconds)
            val added =
                world.projects
                    .add(P2_DIR)
                    .getOrNull()
                    .shouldNotBeNull()
            runCurrent()

            world.runtime.listedAtMillis shouldBe millis(0, 500)
            world.session(S1).project shouldBe added.id
            advance(2.seconds)
            world.runtime.listedAtMillis shouldBe millis(0, 500, 2_500)
        }

    @Test
    fun `removing a project detaches its sessions at once`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()
            world.session(S1).project shouldBe P1

            advance(500.milliseconds)
            world.projects.remove(P1)
            runCurrent()

            world.runtime.listedAtMillis shouldBe millis(0, 500)
            world.session(S1).project shouldBe null
        }

    @Test
    fun `renaming a project does not list again`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()

            advance(500.milliseconds)
            world.projects.rename(P1, "renamed")
            advance(1_499.milliseconds)

            world.runtime.listedAtMillis shouldBe millis(0)
        }

    @Test
    fun `project change during a listing lists again right after it`() =
        runTest {
            val world =
                monitorWorld(sessions = listOf(working(S1, cwd = P2_DIR)), listingTime = 1.seconds)

            advance(500.milliseconds)
            val added =
                world.projects
                    .add(P2_DIR)
                    .getOrNull()
                    .shouldNotBeNull()
            advance(500.milliseconds)
            world.runtime.listedAtMillis shouldBe millis(0, 1_000)
            advance(1.seconds)

            // Not the proof of the wakeup (the first listing already reads the new dirs when it publishes);
            // listedAtMillis above is. This only checks that the session ends up attributed.
            world.session(S1).project shouldBe added.id
        }

    @Test
    fun `project change while the runtime is down re-attributes stale sessions`() =
        runTest {
            val world = monitorWorld(sessions = listOf(working(S1)))
            runCurrent()
            world.runtime.available(false)
            advance(2.seconds)

            advance(500.milliseconds)
            world.projects.remove(P1)
            runCurrent()

            world.runtime.listedAtMillis shouldBe millis(0, 2_000, 2_500)
            world.session(S1).project shouldBe null
            world.monitor.snapshot.value.status shouldBe
                SnapshotStatus.Stale(lastFreshAt = T0, problem = RuntimeProblem.NOT_INSTALLED)
        }
}
