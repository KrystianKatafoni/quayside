package dev.agentmanager.sessions.impl

import dev.agentmanager.agentruntime.api.AgentRuntime
import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.warn
import dev.agentmanager.sessions.api.RecentOutput
import dev.agentmanager.sessions.api.SessionError

/**
 * Reads the output tail of a session from [runtime] (AM-411), without listing sessions first.
 *
 * A failure is logged as the session id and the fixed problem only; the output itself is never logged (ENG-C8).
 */
internal class RecentOutputReader(
    private val runtime: AgentRuntime,
    private val logger: Logger,
) {
    suspend fun read(id: SessionId): Outcome<RecentOutput, SessionError> =
        when (val result = runtime.recentOutput(id)) {
            is Outcome.Ok -> {
                Outcome.Ok(RecentOutput(result.value))
            }

            is Outcome.Err -> {
                logger.warn("Session output failed", "session" to id.value, "problem" to result.error.toProblem())
                Outcome.Err(result.error.toSessionError())
            }
        }
}
