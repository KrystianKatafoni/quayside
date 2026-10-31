package dev.agentmanager.sessions.impl

import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimePhase
import dev.agentmanager.agentruntime.api.RuntimeSession
import dev.agentmanager.agentruntime.api.WaitingFor
import dev.agentmanager.sessions.api.RuntimeProblem
import dev.agentmanager.sessions.api.SessionError
import dev.agentmanager.sessions.api.SessionState

/**
 * FR-S2, docs/05: supervisor phase and waiting reason → state. Details (question, tool) come with hooks (AM-603).
 *
 * A [RuntimePhase.BLOCKED] session asks for permission only for an explicit [WaitingFor.Permission];
 * [WaitingFor.Input], [WaitingFor.Other] and a missing reason all mean it waits for input.
 */
internal fun RuntimeSession.toSessionState(): SessionState =
    when (phase) {
        RuntimePhase.WORKING -> {
            SessionState.Working
        }

        RuntimePhase.BLOCKED -> {
            when (waitingFor) {
                WaitingFor.Permission -> SessionState.AwaitingPermission(tool = null)
                WaitingFor.Input, is WaitingFor.Other, null -> SessionState.AwaitingInput(question = null)
            }
        }

        RuntimePhase.DONE -> {
            SessionState.Done
        }

        RuntimePhase.FAILED -> {
            SessionState.Failed
        }

        RuntimePhase.STOPPED -> {
            SessionState.Stopped
        }
    }

/**
 * Fixed category of a listing failure (ENG-C8: drops exit codes, tails and details).
 *
 * [RuntimeError.UnknownSession] has no category of its own: a listing never returns it, so it maps to
 * [RuntimeProblem.COMMAND_FAILED].
 */
internal fun RuntimeError.toProblem(): RuntimeProblem =
    when (this) {
        RuntimeError.NotInstalled -> RuntimeProblem.NOT_INSTALLED
        is RuntimeError.UnsupportedVersion -> RuntimeProblem.UNSUPPORTED_VERSION
        RuntimeError.AgentViewDisabled -> RuntimeProblem.AGENT_VIEW_DISABLED
        RuntimeError.Timeout -> RuntimeProblem.TIMEOUT
        is RuntimeError.CommandFailed, is RuntimeError.UnknownSession -> RuntimeProblem.COMMAND_FAILED
        is RuntimeError.Unparseable -> RuntimeProblem.UNPARSEABLE
    }

/** AM-402: an unknown session keeps its id, every other runtime error becomes its fixed problem. */
internal fun RuntimeError.toSessionError(): SessionError =
    when (this) {
        is RuntimeError.UnknownSession -> SessionError.UnknownSession(id)
        else -> SessionError.Runtime(toProblem())
    }
