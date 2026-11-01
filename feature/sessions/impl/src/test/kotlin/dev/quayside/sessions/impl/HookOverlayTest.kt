package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.WaitingFor
import dev.quayside.agentruntime.api.aConversationId
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.agentruntime.api.anEventMeta
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

/** How hooks are laid over a listing (AM-603 plan, rules of `HookOverlay`). */
internal class HookOverlayTest {
    private val conversation = aConversationId()
    private val lead = 5.seconds

    private fun note(
        signal: HookSignal,
        at: Instant = T0,
        question: String? = null,
        tool: String? = null,
    ) = HookNote(conversation, signal, question, tool, at)

    private fun merge(
        session: RuntimeSession,
        note: HookNote?,
        now: Instant,
    ) = HookOverlay.apply(listOf(session), listOfNotNull(note).associateBy { it.conversationId }, now, lead)

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `lays a hook over the listing`(
        case: String,
        listed: RuntimeSession,
        signal: HookSignal,
        secondsLater: Long,
        expected: Shown,
    ) {
        val merged = merge(listed, note(signal, question = "Which?"), T0.plusSeconds(secondsLater))

        withClue(case) {
            merged.sessions.single().phase shouldBe expected.phase
            merged.sessions.single().waitingFor shouldBe expected.waitingFor
            merged.notes.isNotEmpty() shouldBe expected.noteKept
            merged.details.containsKey(listed.id) shouldBe expected.noteKept
        }
    }

    /** What the merged listing shows for the session and whether the note stays. */
    internal data class Shown(
        val phase: RuntimePhase,
        val waitingFor: WaitingFor?,
        val noteKept: Boolean,
    )

    @Test
    fun `a kept note brings its question, tool and time`() {
        val listed = aRuntimeSession(phase = RuntimePhase.BLOCKED, waitingFor = WaitingFor.Permission)

        val merged = merge(listed, note(HookSignal.PERMISSION, tool = "Bash"), T0.plusSeconds(60))

        merged.details shouldBe mapOf(listed.id to HookDetail(T0, question = null, tool = "Bash"))
    }

    @Test
    fun `a session without conversation id or without a note is left alone`() {
        val noConversation = aRuntimeSession(conversationId = null)
        val other = aRuntimeSession(id = SessionId("bbbb0002"), conversationId = ConversationId("other"))
        val notes = mapOf(conversation to note(HookSignal.NEEDS_INPUT))

        val merged = HookOverlay.apply(listOf(noConversation, other), notes, T0, lead)

        merged.sessions shouldBe listOf(noConversation, other)
        merged.notes shouldBe emptyMap()
        merged.details shouldBe emptyMap()
    }

    @Test
    fun `a note of a conversation that is not listed is dropped`() {
        val merged = HookOverlay.apply(emptyList(), mapOf(conversation to note(HookSignal.FAILED)), T0, lead)

        merged.notes shouldBe emptyMap()
    }

    @Test
    fun `events become notes and a session end does not`() {
        val meta = anEventMeta(conversationId = conversation, at = T0)

        AgentEvent.NeedsInput(meta, "Q?").toNote() shouldBe note(HookSignal.NEEDS_INPUT, question = "Q?")
        AgentEvent.PermissionRequested(meta, "Bash").toNote() shouldBe note(HookSignal.PERMISSION, tool = "Bash")
        AgentEvent.TurnEnded(meta, "Done").toNote() shouldBe note(HookSignal.TURN_ENDED)
        AgentEvent.Failed(meta, "api_error").toNote() shouldBe note(HookSignal.FAILED)
        AgentEvent.SessionEnded(meta, "exit").toNote() shouldBe null
    }

    @Test
    fun `a note hides the question in toString`() {
        note(HookSignal.NEEDS_INPUT, question = "secret plan").toString().contains("secret") shouldBe false
    }

    companion object {
        private fun listed(
            phase: RuntimePhase,
            waitingFor: WaitingFor? = null,
        ) = aRuntimeSession(phase = phase, waitingFor = waitingFor)

        @JvmStatic
        @Suppress("LongMethod")
        fun cases(): Stream<Arguments> =
            Stream.of(
                // case, listed, hook, seconds after the hook, what is shown
                arguments(
                    "supervisor lags: input hook leads",
                    listed(RuntimePhase.WORKING),
                    HookSignal.NEEDS_INPUT,
                    2L,
                    Shown(RuntimePhase.BLOCKED, WaitingFor.Input, true),
                ),
                arguments(
                    "supervisor lags: permission hook leads",
                    listed(RuntimePhase.WORKING),
                    HookSignal.PERMISSION,
                    4L,
                    Shown(RuntimePhase.BLOCKED, WaitingFor.Permission, true),
                ),
                arguments(
                    "supervisor lags: turn end leads",
                    listed(RuntimePhase.WORKING),
                    HookSignal.TURN_ENDED,
                    1L,
                    Shown(RuntimePhase.DONE, null, true),
                ),
                arguments(
                    "supervisor lags: failure leads",
                    listed(RuntimePhase.WORKING),
                    HookSignal.FAILED,
                    1L,
                    Shown(RuntimePhase.FAILED, null, true),
                ),
                arguments(
                    "lead over: polling wins",
                    listed(RuntimePhase.WORKING),
                    HookSignal.NEEDS_INPUT,
                    5L,
                    Shown(RuntimePhase.WORKING, null, false),
                ),
                arguments(
                    "poll agrees: the listing keeps its own reason",
                    listed(RuntimePhase.BLOCKED, WaitingFor.Other("custom")),
                    HookSignal.NEEDS_INPUT,
                    60L,
                    Shown(RuntimePhase.BLOCKED, WaitingFor.Other("custom"), true),
                ),
                arguments(
                    "poll agrees on done",
                    listed(RuntimePhase.DONE),
                    HookSignal.TURN_ENDED,
                    60L,
                    Shown(RuntimePhase.DONE, null, true),
                ),
                arguments(
                    "poll disagrees: stopped is not overruled",
                    listed(RuntimePhase.STOPPED),
                    HookSignal.NEEDS_INPUT,
                    1L,
                    Shown(RuntimePhase.STOPPED, null, false),
                ),
                arguments(
                    "poll disagrees: done is not overruled by an older input hook",
                    listed(RuntimePhase.DONE),
                    HookSignal.NEEDS_INPUT,
                    1L,
                    Shown(RuntimePhase.DONE, null, false),
                ),
            )
    }
}
