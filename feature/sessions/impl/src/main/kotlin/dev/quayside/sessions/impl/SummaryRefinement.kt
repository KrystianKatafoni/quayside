package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import dev.quayside.summaries.api.SummaryError
import dev.quayside.summaries.api.SummaryRefiner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Runs the model summary of each `Stop` in the background (ADR 0019, AM-604 B): at most one model call at a time
 * across all conversations, and a newer `Stop` of a conversation cancels the older refinement of the same
 * conversation. A failed refinement logs one `warn` with a fixed category and nothing else; the quick summary stays.
 * The message and the summary are user data (ENG-C8): never logged.
 *
 * [start] is called under the mutex of `SessionsState`, which also guards [jobs]. [scope] should dispatch the job
 * instead of running it inside [start] (not `Dispatchers.Unconfined`): an immediate dispatcher would start the
 * model call under the state's mutex, and the order of publications would depend on the dispatcher.
 */
internal class SummaryRefinement(
    private val refiner: SummaryRefiner,
    private val scope: CoroutineScope,
    private val logger: Logger,
) {
    private val oneCall = Semaphore(1)
    private val jobs = mutableMapOf<ConversationId, Job>()

    /** Cancels the running refinement of [conversation] (a conversation that left the list). */
    fun cancel(conversation: ConversationId) {
        jobs.remove(conversation)?.cancel()
    }

    /** Cancels the running refinement of [conversation], then refines [message] and hands the summary to [deliver]. */
    fun start(
        conversation: ConversationId,
        message: String,
        deliver: suspend (String) -> Unit,
    ) {
        cancel(conversation)
        jobs[conversation] =
            scope.launch {
                // withPermit frees the permit in a finally: after a result, an error and a cancellation alike.
                when (val result = oneCall.withPermit { refiner.refine(message) }) {
                    is Outcome.Ok -> deliver(result.value)
                    is Outcome.Err -> logger.warn("summary refinement failed", "reason" to result.error.category())
                }
            }
    }

    private fun SummaryError.category(): String =
        when (this) {
            SummaryError.Empty -> "empty"
            SummaryError.Unavailable -> "unavailable"
            SummaryError.Failed -> "failed"
        }
}
