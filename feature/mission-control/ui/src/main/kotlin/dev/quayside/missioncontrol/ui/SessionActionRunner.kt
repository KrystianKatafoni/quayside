package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import dev.quayside.sessions.api.ResumedSession
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError

/**
 * Runs one action, logs the session id and a fixed category only (ENG-C8); null = success. A successful resume
 * is handed to `onResumed` before `run` returns, since the live session has a new id.
 */
internal class SessionActionRunner(
    private val commands: SessionCommands,
    private val logger: Logger,
) {
    suspend fun run(
        action: SessionAction,
        id: SessionId,
        onResumed: (ResumedSession) -> Unit = {},
    ): UiText? =
        when (val outcome = execute(action, id, onResumed)) {
            is Outcome.Ok -> {
                logger.info(action.doneMessage(), "sessionId" to id.value)
                null
            }

            is Outcome.Err -> {
                val reason = SessionActionMessages.category(outcome.error)
                logger.warn("session ${action.name.lowercase()} failed", "sessionId" to id.value, "reason" to reason)
                SessionActionMessages.reason(outcome.error)
            }
        }

    private suspend fun execute(
        action: SessionAction,
        id: SessionId,
        onResumed: (ResumedSession) -> Unit,
    ): Outcome<Any, SessionError> =
        when (action) {
            SessionAction.STOP -> commands.stop(id)
            SessionAction.RESPAWN -> commands.respawn(id)
            SessionAction.RESUME -> commands.resume(id).also { (it as? Outcome.Ok)?.value?.let(onResumed) }
            SessionAction.REMOVE -> commands.remove(id)
        }
}

private fun SessionAction.doneMessage(): String =
    when (this) {
        SessionAction.STOP -> "session stopped"
        SessionAction.RESPAWN -> "session respawned"
        SessionAction.RESUME -> "session resumed"
        SessionAction.REMOVE -> "session removed"
    }

/** Fixed texts and log categories of a failed action (plan, table 2); never the id, the name or a path. */
internal object SessionActionMessages {
    fun reason(error: SessionError): UiText =
        UiText(
            when (error) {
                is SessionError.UnknownSession -> "action.reason.unknownSession"

                is SessionError.SessionActive -> "action.reason.sessionActive"

                is SessionError.Runtime -> "action.reason.runtime.${error.problem.name}"

                is SessionError.ResumeUnavailable -> "action.reason.resume.${error.problem.name}"

                is SessionError.UnknownProject,
                is SessionError.StopIncomplete,
                is SessionError.SubfolderUnavailable,
                -> "action.reason.other"
            },
        )

    fun category(error: SessionError): String =
        when (error) {
            is SessionError.UnknownSession -> "unknownSession"
            is SessionError.SessionActive -> "sessionActive"
            is SessionError.Runtime -> "runtime.${error.problem.name}"
            is SessionError.ResumeUnavailable -> "resumeUnavailable.${error.problem.name}"
            is SessionError.UnknownProject -> "unknownProject"
            is SessionError.StopIncomplete -> "stopIncomplete"
            is SessionError.SubfolderUnavailable -> "subfolderUnavailable"
        }
}
