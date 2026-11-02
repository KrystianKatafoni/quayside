package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.awaitCancellation

/** The fake passes the same contract as the real adapter (ENG-T2). */
class FakeOneShotPromptContractTest : OneShotPromptContract() {
    override fun create(
        behaviour: Behaviour,
        answer: String,
    ): OneShotPrompt =
        FakeOneShotPrompt {
            when (behaviour) {
                Behaviour.ANSWERS -> Outcome.Ok(answer)
                Behaviour.NOT_INSTALLED -> Outcome.Err(RuntimeError.NotInstalled)
                Behaviour.SLOW -> awaitCancellation()
                Behaviour.FAILS -> Outcome.Err(RuntimeError.CommandFailed(3, "Error: cannot run '***'"))
            }
        }
}
