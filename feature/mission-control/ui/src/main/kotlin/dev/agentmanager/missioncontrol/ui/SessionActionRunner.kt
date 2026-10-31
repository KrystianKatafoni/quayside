package dev.agentmanager.missioncontrol.ui

import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.info
import dev.agentmanager.core.domain.warn
import dev.agentmanager.sessions.api.SessionCommands
import dev.agentmanager.sessions.api.SessionError

/** Runs one action, logs the session id and a fixed category only (ENG-C8); null = success. */
internal class SessionActionRunner(
    private val commands: SessionCommands,
    private val logger: Logger,
) {
    suspend fun run(
        action: SessionAction,
        id: SessionId,
    ): UiText? {
        val outcome =
            when (action) {
                SessionAction.STOP -> commands.stop(id)
                SessionAction.RESPAWN -> commands.respawn(id)
                SessionAction.REMOVE -> commands.remove(id)
            }
        val (done, verb) =
            when (action) {
                SessionAction.STOP -> "session stopped" to "stop"
                SessionAction.RESPAWN -> "session respawned" to "respawn"
                SessionAction.REMOVE -> "session removed" to "remove"
            }
        return when (outcome) {
            is Outcome.Ok -> {
                logger.info(done, "sessionId" to id.value)
                null
            }

            is Outcome.Err -> {
                val reason = SessionActionMessages.category(outcome.error)
                logger.warn("session $verb failed", "sessionId" to id.value, "reason" to reason)
                SessionActionMessages.reason(outcome.error)
            }
        }
    }
}

/** Fixed texts and log categories of a failed action (plan, table 2); never the id, the name or a path. */
internal object SessionActionMessages {
    fun reason(error: SessionError): UiText =
        UiText(
            when (error) {
                is SessionError.UnknownSession -> "action.reason.unknownSession"
                is SessionError.SessionActive -> "action.reason.sessionActive"
                is SessionError.Runtime -> "action.reason.runtime.${error.problem.name}"
                is SessionError.UnknownProject, is SessionError.StopIncomplete -> "action.reason.other"
            },
        )

    fun category(error: SessionError): String =
        when (error) {
            is SessionError.UnknownSession -> "unknownSession"
            is SessionError.SessionActive -> "sessionActive"
            is SessionError.Runtime -> "runtime.${error.problem.name}"
            is SessionError.UnknownProject -> "unknownProject"
            is SessionError.StopIncomplete -> "stopIncomplete"
        }
}
