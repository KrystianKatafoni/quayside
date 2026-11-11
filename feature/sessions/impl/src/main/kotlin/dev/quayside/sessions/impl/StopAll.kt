package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.info
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionError

/** `SessionCommands.stopAllIn` after the targets are chosen: stops each one and publishes the stopped ones. */
internal class StopAll(
    private val runtime: AgentRuntime,
    private val state: SessionsState,
    private val logger: Logger,
) {
    /** Stops [targets] and publishes the stopped ones over one fresh listing. */
    suspend fun run(targets: List<Session>): Outcome<Int, SessionError> {
        if (targets.isEmpty()) return Outcome.Ok(0)
        val (moves, failed) = stopEach(targets)
        state.refresh(moves)
        logger.info("Sessions of a project stopped", "count" to moves.size, "failed" to failed.size)
        return if (failed.isEmpty()) {
            Outcome.Ok(moves.size)
        } else {
            Outcome.Err(SessionError.StopIncomplete(moves.size, failed))
        }
    }

    /** Stops every target: the effects of the stopped ones and the ids of those still running. */
    private suspend fun stopEach(targets: List<Session>): Pair<List<Expected>, List<SessionId>> {
        val moves = mutableListOf<Expected>()
        val failed = mutableListOf<SessionId>()
        targets.forEach { session ->
            when (val stopped = runtime.stop(session.id)) {
                is Outcome.Ok -> {
                    moves += Expected.Moves(session.id, session.phase(), RuntimePhase.STOPPED, state.expiry())
                }

                is Outcome.Err -> {
                    // Gone meanwhile: not active any more, so nothing to stop.
                    if (stopped.error !is RuntimeError.UnknownSession) {
                        logger.commandFailed("stop", session.id, stopped.error)
                        failed += session.id
                    }
                }
            }
        }
        return moves to failed
    }
}
