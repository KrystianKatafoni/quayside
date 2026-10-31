package dev.agentmanager.sessions.impl

import dev.agentmanager.agentruntime.api.RuntimePhase
import dev.agentmanager.agentruntime.api.RuntimeSession
import dev.agentmanager.agentruntime.api.WaitingFor
import dev.agentmanager.agentruntime.api.aRuntimeSession
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.testing.T0
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant

/** The pure command overlay (AM-404 plan, table "CommandOverlay.apply"). */
internal class CommandOverlayTest {
    @Test
    fun `appearing session is added until the listing shows it`() {
        val started = session(S1)
        val appears = Expected.Appears(started, UNTIL)
        val other = session(S2)

        val before = CommandOverlay.apply(listOf(other), listOf(appears), NOW)

        before.sessions shouldContainExactlyInAnyOrder listOf(other, started)
        before.pending shouldBe listOf(appears)

        // The supervisor lists it with its own data (here already waiting): the listing wins.
        val listed = started.copy(phase = RuntimePhase.BLOCKED, waitingFor = WaitingFor.Input, pid = 99L)
        val after = CommandOverlay.apply(listOf(other, listed), listOf(appears), NOW)

        after.sessions shouldContainExactlyInAnyOrder listOf(other, listed)
        after.pending.shouldBeEmpty()
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("pendingMoves")
    fun `moved session keeps the expected phase while the listing shows the old one`(
        from: RuntimePhase,
        to: RuntimePhase,
    ) {
        val waitingFor = if (from == RuntimePhase.BLOCKED) WaitingFor.Permission else null
        val listed = session(S1, phase = from, waitingFor = waitingFor)
        val moves = Expected.Moves(SessionId(S1), from = from, to = to, until = UNTIL)

        val result = CommandOverlay.apply(listOf(listed, session(S2)), listOf(moves), NOW)

        result.sessions.map { Triple(it.id, it.phase, it.waitingFor) } shouldContainExactlyInAnyOrder
            listOf(Triple(SessionId(S1), to, null), Triple(SessionId(S2), RuntimePhase.WORKING, null))
        result.sessions.single { it.id == SessionId(S1) }.let { listOf(it.name, it.cwd, it.startedAt) } shouldBe
            listOf(listed.name, listed.cwd, listed.startedAt)
        result.pending shouldBe listOf(moves)
    }

    @ParameterizedTest(name = "listing shows {0}")
    @EnumSource(RuntimePhase::class, names = ["WORKING"], mode = EnumSource.Mode.EXCLUDE)
    fun `moved session follows the listing once it shows another phase`(shown: RuntimePhase) {
        // Stop of a working session: STOPPED confirms it, any other phase means the agent moved on.
        val listed = session(S1, phase = shown)
        val moves = Expected.Moves(SessionId(S1), from = RuntimePhase.WORKING, to = RuntimePhase.STOPPED, until = UNTIL)

        val result = CommandOverlay.apply(listOf(listed), listOf(moves), NOW)

        result shouldBe Overlaid(sessions = listOf(listed), pending = emptyList())
    }

    @Test
    fun `removed session stays hidden until the listing drops it`() {
        val disappears = Expected.Disappears(SessionId(S1), UNTIL)
        val other = session(S2)

        val before =
            CommandOverlay.apply(
                listOf(session(S1, phase = RuntimePhase.DONE), other),
                listOf(disappears),
                NOW,
            )

        before shouldBe Overlaid(sessions = listOf(other), pending = listOf(disappears))

        val after = CommandOverlay.apply(listOf(other), listOf(disappears), NOW)

        after shouldBe Overlaid(sessions = listOf(other), pending = emptyList())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expiredCases")
    fun `expired expectation gives way to the listing`(
        @Suppress("UNUSED_PARAMETER") kind: String,
        expected: Expected,
    ) {
        // S1 is listed as working and S3 is not listed: none of the expectations is confirmed yet.
        val listing = listOf(session(S1), session(S2))

        CommandOverlay.apply(listing, listOf(expected), now = expected.until) shouldBe
            Overlaid(sessions = listing, pending = emptyList())
        CommandOverlay.apply(listing, listOf(expected), now = expected.until.plusMillis(1)) shouldBe
            Overlaid(sessions = listing, pending = emptyList())
    }

    @Test
    fun `expectation just before its expiry still applies`() {
        val moves = Expected.Moves(SessionId(S1), RuntimePhase.WORKING, RuntimePhase.STOPPED, UNTIL)

        val result = CommandOverlay.apply(listOf(session(S1)), listOf(moves), now = UNTIL.minusMillis(1))

        result.sessions.single().phase shouldBe RuntimePhase.STOPPED
        result.pending shouldBe listOf(moves)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vanishedCases")
    fun `expectation for a vanished session is dropped`(
        @Suppress("UNUSED_PARAMETER") kind: String,
        expected: Expected,
    ) {
        val listing = listOf(session(S2))

        CommandOverlay.apply(listing, listOf(expected), NOW) shouldBe
            Overlaid(sessions = listing, pending = emptyList())
    }

    @Test
    fun `newer expectation for the same session wins`() {
        // stop, then respawn before the supervisor showed the stop; it now lists the session stopped.
        val stop = Expected.Moves(SessionId(S1), RuntimePhase.WORKING, RuntimePhase.STOPPED, UNTIL)
        val respawn = Expected.Moves(SessionId(S1), RuntimePhase.STOPPED, RuntimePhase.WORKING, UNTIL.plusSeconds(1))

        val result = CommandOverlay.apply(listOf(session(S1, phase = RuntimePhase.STOPPED)), listOf(stop, respawn), NOW)

        result.sessions.single().phase shouldBe RuntimePhase.WORKING
        result.pending shouldBe listOf(respawn)
    }

    @Test
    fun `removal right after start hides the started session`() {
        val started = session(S1)
        val appears = Expected.Appears(started, UNTIL)
        val disappears = Expected.Disappears(SessionId(S1), UNTIL.plusSeconds(1))

        val result = CommandOverlay.apply(listOf(session(S2)), listOf(appears, disappears), NOW)

        result.sessions.map { it.id } shouldBe listOf(SessionId(S2))
        result.pending.shouldBeEmpty()
    }

    @Test
    fun `stop right after start shows the session stopped`() {
        val started = session(S1, phase = RuntimePhase.WORKING, waitingFor = WaitingFor.Input)
        val appears = Expected.Appears(started, UNTIL)
        val stop = Expected.Moves(SessionId(S1), RuntimePhase.WORKING, RuntimePhase.STOPPED, UNTIL.plusSeconds(1))
        val stopped = started.copy(phase = RuntimePhase.STOPPED, waitingFor = null)

        val first = CommandOverlay.apply(emptyList(), listOf(appears, stop), NOW)
        val second = CommandOverlay.apply(emptyList(), first.pending, NOW)

        first.sessions shouldBe listOf(stopped)
        second.sessions shouldBe listOf(stopped)
        second.pending shouldBe listOf(Expected.Appears(stopped, UNTIL.plusSeconds(1)))
    }

    @Test
    fun `expectations of other sessions do not touch a session`() {
        val listing = listOf(session(S1), session(S2, phase = RuntimePhase.BLOCKED, waitingFor = WaitingFor.Input))
        val moves = Expected.Moves(SessionId(S1), RuntimePhase.WORKING, RuntimePhase.STOPPED, UNTIL)

        val result = CommandOverlay.apply(listing, listOf(moves), NOW)

        result.sessions.single { it.id == SessionId(S2) } shouldBe listing[1]
    }

    @Test
    fun `no expectations leave the listing as it is`() {
        val listing = listOf(session(S1), session(S2, phase = RuntimePhase.DONE))

        CommandOverlay.apply(listing, emptyList(), NOW) shouldBe Overlaid(sessions = listing, pending = emptyList())
    }

    companion object {
        private const val S1 = "aaaa0001"
        private const val S2 = "aaaa0002"
        private const val S3 = "aaaa0003"
        private val NOW: Instant = T0
        private val UNTIL: Instant = T0.plusSeconds(10)

        private fun session(
            id: String,
            phase: RuntimePhase = RuntimePhase.WORKING,
            waitingFor: WaitingFor? = null,
        ): RuntimeSession = aRuntimeSession(id = SessionId(id), phase = phase, waitingFor = waitingFor)

        @JvmStatic
        fun pendingMoves(): List<Arguments> =
            listOf(
                arguments(RuntimePhase.WORKING, RuntimePhase.STOPPED),
                arguments(RuntimePhase.BLOCKED, RuntimePhase.STOPPED),
                arguments(RuntimePhase.DONE, RuntimePhase.WORKING),
                arguments(RuntimePhase.FAILED, RuntimePhase.WORKING),
                arguments(RuntimePhase.STOPPED, RuntimePhase.WORKING),
            )

        @JvmStatic
        fun expiredCases(): List<Arguments> =
            listOf(
                arguments("Appears", Expected.Appears(session(S3), UNTIL)),
                arguments("Moves", Expected.Moves(SessionId(S1), RuntimePhase.WORKING, RuntimePhase.STOPPED, UNTIL)),
                arguments("Disappears", Expected.Disappears(SessionId(S1), UNTIL)),
            )

        @JvmStatic
        fun vanishedCases(): List<Arguments> =
            listOf(
                arguments("Moves", Expected.Moves(SessionId(S1), RuntimePhase.WORKING, RuntimePhase.STOPPED, UNTIL)),
                arguments("Disappears", Expected.Disappears(SessionId(S1), UNTIL)),
            )
    }
}
