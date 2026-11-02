@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.anEventMeta
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.impl.MonitorFixtures.S1
import dev.quayside.sessions.impl.MonitorFixtures.S2
import dev.quayside.sessions.impl.MonitorFixtures.working
import dev.quayside.summaries.api.FakeSummarizer
import dev.quayside.summaries.api.SummariesApi
import dev.quayside.summaries.api.SummaryError
import dev.quayside.summaries.api.SummaryRefiner
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.time.Duration.Companion.seconds

/**
 * The model summary in `sessionsModule` (ADR 0019, AM-604 part B): it replaces the quick summary in the
 * background, one model call at a time, and only for the newest turn of a conversation. Dropping the entries of
 * conversations that left the list is memory hygiene and not observable, so no test covers it.
 */
class SummaryRefinementTest {
    private val refiner = GatedRefiner()

    /** One model call that the test holds until it lets it go with [answer]. */
    private class Call(
        val message: String,
    ) {
        val answer = CompletableDeferred<Outcome<String, SummaryError>>()
        var cancelled = false

        fun finish(summary: String) = answer.complete(Outcome.Ok(summary))
    }

    private class GatedRefiner(
        private val ignoresCancel: Boolean = false,
    ) : SummaryRefiner {
        val calls = mutableListOf<Call>()
        var active = 0
        var maxActive = 0

        override suspend fun refine(lastMessage: String): Outcome<String, SummaryError> {
            val call = Call(lastMessage).also { calls += it }
            maxActive = maxOf(maxActive, ++active)
            try {
                return if (ignoresCancel) withContext(NonCancellable) { call.answer.await() } else call.answer.await()
            } catch (e: CancellationException) {
                call.cancelled = true
                throw e
            } finally {
                active--
            }
        }
    }

    private fun stop(
        conversation: String,
        message: String?,
    ) = AgentEvent.TurnEnded(anEventMeta(conversationId = ConversationId(conversation), at = T0), message)

    private fun TestScope.world(
        gated: GatedRefiner? = refiner,
        summarizer: FakeSummarizer = FakeSummarizer(),
    ): MonitorWorld {
        val sessions =
            listOf(
                working(S1).copy(conversationId = ConversationId(C1)),
                working(S2).copy(conversationId = ConversationId(C2)),
            )
        return monitorWorld(sessions = sessions, summaries = SummariesApi(summarizer, gated)).also { runCurrent() }
    }

    private suspend fun MonitorWorld.stopped(
        conversation: String,
        message: String?,
    ) {
        hooks.emit(stop(conversation, message))
    }

    private fun MonitorWorld.shownSummaries() = listOf(session(S1).summary, session(S2).summary)

    private fun MonitorWorld.logText() = logger.entries.joinToString("\n") { "${it.message} ${it.fields} ${it.cause}" }

    @Test
    fun `model summary replaces the quick one`() =
        runTest {
            val world = world()
            val events = mutableListOf<SessionEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                world.monitor.events.collect {
                    events +=
                        it
                }
            }

            world.stopped(C1, "Confidential first message.")
            runCurrent()

            refiner.calls.single().message shouldBe "Confidential first message."
            world.session(S1).summary shouldBe "Confidential first message."

            refiner.calls.single().finish("Confidential model summary.")
            runCurrent()

            world.session(S1).summary shouldBe "Confidential model summary."
            world.session(S2).summary shouldBe null
            events.single().shouldBeTurnFinishedWith("Confidential first message.")
            world.logText() shouldNotContain "Confidential"
        }

    private fun SessionEvent.shouldBeTurnFinishedWith(summary: String) {
        (this as SessionEvent.TurnFinished).session.summary shouldBe summary
    }

    @Test
    fun `newer Stop cancels the older refinement`() =
        runTest {
            val world = world()
            world.stopped(C1, "Turn one.")
            runCurrent()

            world.stopped(C1, "Turn two.")
            runCurrent()

            refiner.calls[0].cancelled shouldBe true
            refiner.calls.map { it.message } shouldBe listOf("Turn one.", "Turn two.")
            world.session(S1).summary shouldBe "Turn two."

            refiner.calls[1].finish("Model two.")
            runCurrent()

            world.session(S1).summary shouldBe "Model two."
            refiner.maxActive shouldBe 1
        }

    @Test
    fun `late result of an older turn is ignored`() =
        runTest {
            val stubborn = GatedRefiner(ignoresCancel = true)
            val world = world(stubborn)
            world.stopped(C1, "Turn one.")
            runCurrent()
            world.stopped(C1, "Turn two.")
            runCurrent()

            stubborn.calls[0].finish("Old model.")
            runCurrent()

            world.session(S1).summary shouldBe "Turn two."
            stubborn.calls shouldHaveSize 2

            stubborn.calls[1].finish("New model.")
            runCurrent()

            world.session(S1).summary shouldBe "New model."
        }

    @Test
    fun `one model call at a time`() =
        runTest {
            val world = world()

            world.stopped(C1, "Message one.")
            world.stopped(C2, "Message two.")
            runCurrent()

            refiner.calls shouldHaveSize 1
            refiner.maxActive shouldBe 1

            refiner.calls[0].finish("Model one.")
            runCurrent()

            refiner.calls[0].cancelled shouldBe false
            refiner.calls.map { it.message } shouldBe listOf("Message one.", "Message two.")
            refiner.calls[1].finish("Model two.")
            runCurrent()

            world.shownSummaries() shouldBe listOf("Model one.", "Model two.")
            refiner.maxActive shouldBe 1
        }

    @ParameterizedTest
    @EnumSource(Failure::class)
    fun `refine error keeps the quick summary and logs no text`(failure: Failure) =
        runTest {
            val world = world()
            world.stopped(C1, "Confidential first message.")
            runCurrent()

            refiner.calls
                .single()
                .answer
                .complete(Outcome.Err(failure.error))
            runCurrent()

            world.session(S1).summary shouldBe "Confidential first message."
            world.logger.entriesAt(LogLevel.WARN) shouldHaveSize 1
            world.logText() shouldNotContain "Confidential"
        }

    @Test
    fun `an error frees the model for the next conversation`() =
        runTest {
            val world = world()
            world.stopped(C1, "Message one.")
            world.stopped(C2, "Message two.")
            runCurrent()

            refiner.calls[0].answer.complete(Outcome.Err(SummaryError.Failed))
            runCurrent()

            refiner.calls shouldHaveSize 2
            refiner.calls[1].finish("Model two.")
            runCurrent()
            world.shownSummaries() shouldBe listOf("Message one.", "Model two.")
        }

    // "Model off" needs no test here: without a refiner the module builds no SummaryRefinement (the type guarantees
    // it). SummariesModuleTest and SummaryFlowTest cover that the switch leaves the refiner out.
    @Test
    fun `late result of an older turn is ignored after the conversation came back`() =
        runTest {
            val stubborn = GatedRefiner(ignoresCancel = true)
            val world = world(stubborn)
            world.stopped(C1, "Turn one.")
            runCurrent()
            world.fake.remove(SessionId(S1))
            advanceTimeBy(2.seconds)
            runCurrent()
            world.fake.hasSession(working(S1).copy(conversationId = ConversationId(C1)))
            advanceTimeBy(2.seconds)
            runCurrent()

            world.stopped(C1, "Turn two.")
            runCurrent()

            world.session(S1).summary shouldBe "Turn two."

            stubborn.calls[0].finish("Old model.")
            runCurrent()

            world.session(S1).summary shouldBe "Turn two."
            stubborn.calls shouldHaveSize 2

            stubborn.calls[1].finish("New model.")
            runCurrent()

            world.session(S1).summary shouldBe "New model."
        }

    @Test
    fun `unlisted conversation starts no refinement`() =
        runTest {
            val world = world()

            world.stopped("nobody", "Turn one.")
            runCurrent()

            refiner.calls.shouldBeEmpty()
        }

    @Test
    fun `turn without a message starts no refinement`() =
        runTest {
            val world = world()

            world.stopped(C1, null)
            world.stopped(C1, "")
            runCurrent()

            refiner.calls.shouldBeEmpty()
        }

    /** The errors a refiner can answer with; each keeps the quick summary. */
    enum class Failure(
        val error: SummaryError,
    ) {
        EMPTY(SummaryError.Empty),
        UNAVAILABLE(SummaryError.Unavailable),
        FAILED(SummaryError.Failed),
    }

    private companion object {
        const val C1 = "conversation-1"
        const val C2 = "conversation-2"
    }
}
