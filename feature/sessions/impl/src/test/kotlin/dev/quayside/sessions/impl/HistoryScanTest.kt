@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.HistoryCall
import dev.quayside.agentruntime.api.HistoryError
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.aConversationId
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.FakeSessionHistorySettings
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.SessionHistorySettings
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.P1_DIR
import dev.quayside.sessions.impl.MonitorFixtures.P2_DIR
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * When `sessionsModule` scans the session files and what it does with the result (FR-P9, ADR 0032): at start, every
 * 60 s, at once after a change of the settings or of the project dirs. Virtual time only (`TestClock` semantics via
 * the scheduler); the scan is observed through the calls of a `FakeSessionHistory`.
 */
class HistoryScanTest {
    private val second = Project(ProjectId("p2"), P2_DIR, "p2")

    private fun diskIds(world: PastHistoryWorld) =
        world.api.monitor.snapshot.value.sessions
            .filter { it.origin == SessionOrigin.DISK }
            .map { it.id.value }

    @Test
    fun `scans at start with the project dirs and the default age`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))

            world.history.calls shouldBe listOf(HistoryCall(listOf(P1_DIR), T0.minus(Duration.ofDays(30))))
            diskIds(world) shouldBe listOf(uuidOf(1))
        }

    @Test
    fun `scans again after 60 s and not before`() =
        runTest {
            val world = pastWorld()

            advance(59.seconds)
            world.history.calls shouldHaveSize 1

            advance(1.seconds)
            world.history.calls shouldHaveSize 2
            advance(60.seconds)
            world.history.calls shouldHaveSize 3
        }

    @Test
    fun `a file written after the first scan is listed by the next scan`() =
        runTest {
            val world = pastWorld()
            diskIds(world) shouldBe emptyList()

            world.history.put(conversation(1))
            advance(60.seconds)

            diskIds(world) shouldBe listOf(uuidOf(1))
        }

    @Test
    fun `a settings change scans at once with the new age`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))

            world.settings.update { it.copy(maxAge = HistoryAge.DAYS_7) }
            runCurrent()

            world.history.calls shouldHaveSize 2
            world.history.calls
                .last()
                .since shouldBe T0.minus(Duration.ofDays(7))

            world.settings.update { it.copy(maxAge = HistoryAge.UNLIMITED) }
            runCurrent()

            world.history.calls
                .last()
                .since shouldBe null
        }

    @Test
    fun `a change of the project dirs scans at once with all dirs`() =
        runTest {
            val world = pastWorld()

            world.projects.add(P2_DIR, "p2")
            runCurrent()

            world.history.calls shouldHaveSize 2
            world.history.calls
                .last()
                .dirs shouldBe listOf(P1_DIR, P2_DIR)
        }

    @Test
    fun `a failed scan keeps the last disk sessions and logs once`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))
            world.history.failing(HistoryError.UNREADABLE)

            advance(60.seconds)
            advance(60.seconds)
            advance(60.seconds)

            diskIds(world) shouldBe listOf(uuidOf(1))
            world.history.calls shouldHaveSize 4
            world.logger.entriesAt(LogLevel.WARN) shouldHaveSize 1
        }

    @Test
    fun `a failed scan does not make the snapshot stale`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)))
            // every published status counts: the next poll (2 s) would hide a Stale one at the end of the wait
            val statuses = mutableListOf<SnapshotStatus>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                world.api.monitor.snapshot
                    .collect { statuses += it.status }
            }
            world.history.failing(HistoryError.UNREADABLE)

            advance(60.seconds)

            statuses.none { it is SnapshotStatus.Stale } shouldBe true
            world.api.monitor.snapshot.value.status
                .shouldBeInstanceOf<SnapshotStatus.Fresh>()
        }

    @Test
    fun `a failed supervisor listing keeps each disk session once`() =
        runTest {
            val world = pastWorld(listOf(conversation(1), conversation(2)))
            world.runtime.failNext(RuntimeError.CommandFailed(1, ""))

            advance(2.seconds)

            diskIds(world).sorted() shouldBe listOf(uuidOf(1), uuidOf(2))
            world.api.monitor.snapshot.value.status
                .shouldBeInstanceOf<SnapshotStatus.Stale>()
        }

    @Test
    fun `a removed project leaves no disk session and no history key even when the next scan fails`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)), extraProjects = listOf(second))
            world.history.put(conversation(2, cwd = P2_DIR))
            advance(60.seconds)
            diskIds(world).toSet() shouldBe setOf(uuidOf(1), uuidOf(2))
            world.history.failing(HistoryError.UNREADABLE)

            world.projects.remove(second.id)
            advance(2.seconds)

            diskIds(world) shouldBe listOf(uuidOf(1))
            world.api.monitor.snapshot.value.history.keys
                .map { it.value } shouldBe listOf("p1")
        }

    @Test
    fun `a scan that works again after a failure lists the new files`() =
        runTest {
            val world = pastWorld()
            world.history.failing(HistoryError.UNREADABLE)
            advance(60.seconds)
            world.history.put(conversation(2))
            world.history.failing(null)

            advance(60.seconds)

            diskIds(world) shouldBe listOf(uuidOf(2))
        }

    @Test
    fun `the supervisor learning a conversation removes its disk session at the next listing, before the next scan`() =
        runTest {
            val world = pastWorld(listOf(conversation(1), conversation(2)))
            diskIds(world).toSet() shouldBe setOf(uuidOf(1), uuidOf(2))

            world.runtime.hasSession(
                aRuntimeSession(id = SessionId("aaaa0001"), conversationId = aConversationId(uuidOf(1)), cwd = P1_DIR),
            )
            advance(2.seconds)

            diskIds(world) shouldBe listOf(uuidOf(2))
            world.history.calls shouldHaveSize 1
        }

    @Test
    fun `logs carry counts only, never titles, paths or conversation ids`() =
        runTest {
            val world = pastWorld(listOf(conversation(1, title = "secret plan")))
            world.history.failing(HistoryError.UNREADABLE)
            advance(60.seconds)

            val text = world.logger.entries.joinToString(" ") { "${it.message} ${it.fields}" }

            listOf("secret plan", "/Users/test", uuidOf(1)).forEach { (it in text) shouldBe false }
        }

    @Test
    fun `without a history source there are no disk sessions and no history keys`() =
        runTest {
            val world = monitorWorld()
            runCurrent()

            world.monitor.snapshot.value.history shouldBe emptyMap()
        }

    @Test
    fun `every project of the list has a history key after the first scan`() =
        runTest {
            val world = pastWorld(listOf(conversation(1)), extraProjects = listOf(second))

            world.api.monitor.snapshot.value.history.keys
                .map { it.value } shouldBe listOf("p1", "p2")
        }

    @Test
    fun `settings given by the caller are used for the first scan`() =
        runTest {
            val settings = FakeSessionHistorySettings(SessionHistorySettings(maxAge = HistoryAge.DAYS_90))

            val world = pastWorld(settings = settings)

            world.history.calls
                .single()
                .since shouldBe T0.minus(Duration.ofDays(90))
        }
}
