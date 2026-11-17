@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.WaitingFor
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.impl.MonitorFixtures.S1
import dev.quayside.sessions.impl.MonitorFixtures.working
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The listing of `sessionsModule` over many accounts (AM-1003 plan, "SessionsAccountsTest"): merged sessions, a
 * failing account, a changing set of accounts. Commands per account: [SessionsAccountsCommandsTest].
 */
class SessionsAccountsTest {
    private val onWork = "bbbb0001"

    @Test
    fun `the snapshot lists the sessions of every account with their account`() =
        runTest {
            val world = accountsWorld(listOf(working(S1)), listOf(working(onWork)))
            runCurrent()

            world.listedIds() shouldBe listOf(S1, onWork)
            world.session(S1).account shouldBe AccountId.DEFAULT
            world.session(onWork).account shouldBe WORK
        }

    @Test
    fun `a failing extra account keeps its last sessions, marks the snapshot stale and does not back off`() =
        runTest {
            val world = accountsWorld(listOf(working(S1)), listOf(working(onWork)))
            runCurrent()

            world.work.available(false)
            advance(10.seconds)

            world.listedIds() shouldBe listOf(S1, onWork)
            val stale =
                world.api.monitor.snapshot.value.status
                    .shouldBeInstanceOf<SnapshotStatus.Stale>()
            stale.problem shouldBe RuntimeProblem.NOT_INSTALLED
            stale.lastFreshAt shouldBe atMillis(0)
            world.default.listedAtMillis shouldBe listOf(0L, 2_000, 4_000, 6_000, 8_000, 10_000)
            world.work.listedAtMillis shouldBe listOf(0L, 2_000, 4_000, 6_000, 8_000, 10_000)
        }

    @Test
    fun `an extra account that never answered is stale from its first try, not without a list`() =
        runTest {
            val world = accountsWorld(listOf(working(S1)), listOf(working(onWork)))
            world.work.available(false)

            runCurrent()
            advance(4.seconds)

            world.listedIds() shouldBe listOf(S1)
            world.api.monitor.snapshot.value.status shouldBe
                SnapshotStatus.Stale(atMillis(0), RuntimeProblem.NOT_INSTALLED)
        }

    @Test
    fun `a default session still emits events and activity while an extra account never answered`() =
        runTest {
            val world = accountsWorld(listOf(working(S1)), listOf(working(onWork)))
            world.work.available(false)
            val events = record(world.api.monitor.events)
            runCurrent()

            world.default.fake.setPhase(SessionId(S1), RuntimePhase.BLOCKED, WaitingFor.Input)
            advance(2.seconds)

            events.map { it::class } shouldBe listOf(SessionEvent.BecameBlocked::class)
            world.session(S1).lastActivityAt shouldBe atMillis(2_000)
        }

    @Test
    fun `when several accounts fail the problem of the first one in order is shown`() =
        runTest {
            val world = accountsWorld(listOf(working(S1)), listOf(working(onWork)))
            runCurrent()

            world.default.available(false)
            world.work.fake.failNext(RuntimeError.Timeout)
            advance(2.seconds)

            world.api.monitor.snapshot.value.status
                .shouldBeInstanceOf<SnapshotStatus.Stale>()
                .problem shouldBe RuntimeProblem.NOT_INSTALLED
        }

    @Test
    fun `a failed extra account is fresh again when it answers`() =
        runTest {
            val world = accountsWorld(listOf(working(S1)), listOf(working(onWork)))
            runCurrent()
            world.work.available(false)
            advance(4.seconds)

            world.work.available(true)
            advance(2.seconds)

            world.listedIds() shouldBe listOf(S1, onWork)
            world.api.monitor.snapshot.value.status
                .shouldBeInstanceOf<SnapshotStatus.Fresh>()
        }

    @Test
    fun `when every account fails the snapshot is stale and polling backs off`() =
        runTest {
            val world = accountsWorld(listOf(working(S1)), listOf(working(onWork)))
            world.default.available(false)
            world.work.available(false)
            runCurrent()

            advance(20.seconds)

            world.api.monitor.snapshot.value.status
                .shouldBeInstanceOf<SnapshotStatus.Stale>()
            // Waits after 1..4 failures: 2, 2, 4, 8 s, as with one account.
            world.default.listedAtMillis shouldBe listOf(0L, 2_000, 4_000, 8_000, 16_000)
        }

    @Test
    fun `a removed account drops its sessions at the next listing`() =
        runTest {
            val world = accountsWorld(listOf(working(S1)), listOf(working(onWork)))
            runCurrent()
            world.listedIds() shouldBe listOf(S1, onWork)

            world.extra.value = emptyMap()
            advance(2.seconds)

            world.listedIds() shouldBe listOf(S1)
        }

    @Test
    fun `a new account in extraRuntimes lists again at once`() =
        runTest {
            val world = accountsWorld(listOf(working(S1)), listOf(working(onWork)), withWork = false)
            runCurrent()
            advance(500.milliseconds)
            world.listedIds() shouldBe listOf(S1)

            world.extra.value = mapOf(WORK to world.work)
            runCurrent()

            world.listedIds() shouldBe listOf(S1, onWork)
            world.default.listedAtMillis shouldBe listOf(0L, 500)
        }

    @Test
    fun `colliding ids keep the default account session and log only a count`() =
        runTest {
            val shared = List(COLLISIONS) { "cccc000$it" }
            val own = List(DEFAULT_ONLY) { "dddd000$it" }
            val world = accountsWorld((shared + own).map { working(it) }, (shared + onWork).map { working(it) })

            runCurrent()

            world.sessions.map { it.id.value }.toSet() shouldBe (shared + own + onWork).toSet()
            world.sessions
                .filter { it.id.value in shared }
                .map { it.account }
                .toSet() shouldBe setOf(AccountId.DEFAULT)
            world.session(onWork).account shouldBe WORK
            val logged = world.logger.entries.flatMap { entry -> entry.fields.values.map { it.toString() } }
            logged.contains(COLLISIONS.toString()) shouldBe true
            (shared + own + onWork).forEach { world.logger.entries.toString() shouldNotContain it }
        }

    @Test
    fun `a standing collision is logged once, not at every listing`() =
        runTest {
            val world = accountsWorld(listOf(working(S1)), listOf(working(S1)))

            runCurrent()
            advance(4.seconds)

            world.default.listedAtMillis shouldBe listOf(0L, 2_000, 4_000)
            world.logger.entriesAt(LogLevel.WARN) shouldHaveSize 1
        }

    private companion object {
        const val COLLISIONS = 4
        const val DEFAULT_ONLY = 7
    }
}
