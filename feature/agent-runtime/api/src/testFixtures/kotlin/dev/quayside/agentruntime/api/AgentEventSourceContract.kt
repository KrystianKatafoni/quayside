package dev.quayside.agentruntime.api

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Behaviour every [AgentEventSource] must have (ENG-T2, ADR 0004): a hot stream without replay.
 * Runs on [FakeAgentEventSource] and, from AM-601, on the hook server, where [emit] posts a hook
 * request to it.
 */
public abstract class AgentEventSourceContract {
    /** Returns a fresh source with no events emitted yet. */
    public abstract fun create(): AgentEventSource

    /**
     * Fires [event] into the source returned by the latest [create], the way a Claude Code hook
     * would, and returns once the source has taken it.
     */
    public abstract suspend fun emit(event: AgentEvent)

    private val meta = anEventMeta()

    @Test
    public fun `emitted event reaches the collector`(): Unit =
        runTest {
            val source = create()
            val event = AgentEvent.NeedsInput(meta, question = "Which file?")
            val received = async(start = CoroutineStart.UNDISPATCHED) { source.events.first() }

            emit(event)

            received.await() shouldBe event
        }

    @Test
    public fun `events are delivered in order`(): Unit =
        runTest {
            val source = create()
            val events =
                listOf(
                    AgentEvent.PermissionRequested(meta, tool = "Bash"),
                    AgentEvent.TurnEnded(meta, lastMessage = "Done"),
                    AgentEvent.Failed(meta, kind = "api_error"),
                    AgentEvent.SessionEnded(meta, reason = "exit"),
                )
            val received = async(start = CoroutineStart.UNDISPATCHED) { source.events.take(events.size).toList() }

            events.forEach { emit(it) }

            received.await() shouldBe events
        }

    @Test
    public fun `every collector receives the event`(): Unit =
        runTest {
            val source = create()
            val event = AgentEvent.TurnEnded(meta, lastMessage = "Done")
            val first = async(start = CoroutineStart.UNDISPATCHED) { source.events.first() }
            val second = async(start = CoroutineStart.UNDISPATCHED) { source.events.first() }

            emit(event)

            listOf(first.await(), second.await()) shouldBe listOf(event, event)
        }

    @Test
    public fun `event emitted with no collector is not replayed`(): Unit =
        runTest {
            val source = create()

            emit(AgentEvent.Failed(meta, kind = "api_error"))

            withTimeoutOrNull(1.seconds) { source.events.first() } shouldBe null
        }
}
