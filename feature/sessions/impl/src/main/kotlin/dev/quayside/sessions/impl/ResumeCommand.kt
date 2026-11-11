package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.ResumeSpec
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.info
import dev.quayside.sessions.api.ResumeProblem
import dev.quayside.sessions.api.ResumedSession
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionOrigin

/**
 * `SessionCommands.resume` of one disk session (FR-P9, AM-313): the checks that need no `claude`, then
 * `claude --bg --resume`, then the effect over the next listing. Logs carry the session id and fixed categories only
 * (ENG-C8).
 */
internal class ResumeCommand(
    private val runtime: AgentRuntime,
    private val dirs: StartDirs,
    private val state: SessionsState,
    private val clock: Clock,
    private val logger: Logger,
) {
    /** [session] comes from a fresh listing; anything but a disk session is [SessionError.UnknownSession]. */
    suspend fun run(session: Session): Outcome<ResumedSession, SessionError> {
        val refused =
            if (session.origin == SessionOrigin.DISK) precheck(session) else SessionError.UnknownSession(session.id)
        return if (refused != null) Outcome.Err(refused) else start(session)
    }

    /** The file first, then the directory; an unreadable history is a failed command, not a missing file. */
    private suspend fun precheck(session: Session): SessionError? {
        val onDisk = state.stillOnDisk(session)
        return when {
            onDisk is Outcome.Err -> {
                SessionError.Runtime(RuntimeProblem.COMMAND_FAILED)
            }

            onDisk is Outcome.Ok && !onDisk.value -> {
                SessionError.ResumeUnavailable(session.id, ResumeProblem.FILE_MISSING)
            }

            !dirs.isDirectory(session.cwd) -> {
                SessionError.ResumeUnavailable(session.id, ResumeProblem.DIRECTORY_MISSING)
            }

            else -> {
                null
            }
        }
    }

    private suspend fun start(session: Session): Outcome<ResumedSession, SessionError> {
        val uuid = ConversationId(session.id.value)
        return when (val started = runtime.resume(ResumeSpec(uuid, session.cwd))) {
            is Outcome.Err -> {
                logger.commandFailed("resume", session.id, started.error)
                Outcome.Err(started.error.toSessionError())
            }

            is Outcome.Ok -> {
                val effect = ResumedSessions.effect(session, uuid, started.value, clock.now())
                logger.info("Session command done", "command" to "resume", "session" to session.id.value)
                state.refresh(listOf(Expected.Appears(effect.session, state.expiry())))
                Outcome.Ok(ResumedSession(started.value, effect.copy))
            }
        }
    }
}
