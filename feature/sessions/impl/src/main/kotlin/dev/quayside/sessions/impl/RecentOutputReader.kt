package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.warn
import dev.quayside.sessions.api.RecentOutput
import dev.quayside.sessions.api.SessionError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Reads the output tail of a session from [runtime] (AM-411), without listing sessions first.
 *
 * A failure is logged as the session id and the fixed problem only; the output itself is never logged (ENG-C8).
 */
internal class RecentOutputReader(
    runtime: AgentRuntime,
    private val logger: Logger,
    extra: StateFlow<Map<AccountId, AgentRuntime>> = MutableStateFlow(emptyMap()),
) {
    private val runtimes = AccountRuntimes(runtime, extra)

    /** Reads from the runtime of [account]; an account without a runtime is [SessionError.UnknownSession]. */
    suspend fun read(
        id: SessionId,
        account: AccountId = AccountId.DEFAULT,
    ): Outcome<RecentOutput, SessionError> =
        when (val result = runtimes.of(account)?.recentOutput(id) ?: Outcome.Err(RuntimeError.UnknownSession(id))) {
            is Outcome.Ok -> {
                Outcome.Ok(RecentOutput(result.value))
            }

            is Outcome.Err -> {
                logger.warn("Session output failed", "session" to id.value, "problem" to result.error.toProblem())
                Outcome.Err(result.error.toSessionError())
            }
        }
}
