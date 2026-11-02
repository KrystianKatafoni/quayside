package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Scriptable [OneShotPrompt] for tests of code that asks a model (ENG-I7). Passes [OneShotPromptContract].
 *
 * [respond] decides the answer and may suspend, e.g. to hold a call until the test lets it go. It runs under
 * the timeout of the request: when it takes longer the call ends with [RuntimeError.Timeout], as with the real
 * adapter. By default every call answers `"Model summary."`.
 *
 * [calls] keeps the requests in the order they came in, also the ones still running or cancelled. [active]
 * counts the calls running now and [maxActive] the most that ran at the same time.
 */
public class FakeOneShotPrompt(
    private val respond: suspend (PromptRequest) -> Outcome<String, RuntimeError> = { Outcome.Ok("Model summary.") },
) : OneShotPrompt {
    private val received = mutableListOf<PromptRequest>()
    private val running = AtomicInteger()
    private val peak = AtomicInteger()

    /** Requests passed to [ask] so far, in call order. */
    public val calls: List<PromptRequest> get() = synchronized(received) { received.toList() }

    /** Calls that have started and not yet ended. */
    public val active: Int get() = running.get()

    /** The most calls that were [active] at the same time. */
    public val maxActive: Int get() = peak.get()

    override suspend fun ask(request: PromptRequest): Outcome<String, RuntimeError> {
        synchronized(received) { received += request }
        peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
        try {
            return withTimeoutOrNull(request.timeout) { respond(request) } ?: Outcome.Err(RuntimeError.Timeout)
        } finally {
            running.decrementAndGet()
        }
    }
}
