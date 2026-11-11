package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.PermissionMode
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.RuntimeSession
import dev.quayside.agentruntime.api.StartSpec
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.getOrElse
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.ResumedSession
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SessionPermissionMode
import dev.quayside.sessions.api.SessionState
import java.nio.file.Path
import java.time.Instant

/**
 * [SessionCommands] over [AgentRuntime], publishing through the shared [SessionsState] (AM-404 plan).
 *
 * `stop`, `respawn` and `remove` check the session against a fresh listing first; every successful
 * command then refreshes with its effect as an [Expected], so the snapshot shows it before the command
 * returns. A failed refresh after a successful command still returns `Ok` (the effect happened). Logs
 * carry session ids, command names and [dev.quayside.sessions.api.RuntimeProblem] only (ENG-C8).
 */
internal class RuntimeSessionCommands(
    private val runtime: AgentRuntime,
    private val dirs: StartDirs,
    private val state: SessionsState,
    private val clock: Clock,
    private val logger: Logger,
) : SessionCommands {
    private val resumeCommand = ResumeCommand(runtime, dirs, state, clock, logger)
    private val stopAll = StopAll(runtime, state, logger)

    override suspend fun start(
        project: ProjectId,
        spec: NewSessionSpec,
    ): Outcome<SessionId, SessionError> {
        val dir = dirs.resolve(project, spec.subfolder).getOrElse { return Outcome.Err(it) }
        return when (val started = runtime.start(spec.toStartSpec(dir))) {
            is Outcome.Err -> {
                failed("start", null, started.error)
            }

            is Outcome.Ok -> {
                val id = started.value
                val session =
                    RuntimeSession(
                        id = id,
                        conversationId = null,
                        name = spec.name ?: id.value,
                        cwd = dir,
                        phase = RuntimePhase.WORKING,
                        waitingFor = null,
                        startedAt = clock.now(),
                        pid = null,
                    )
                done("start", id, Expected.Appears(session, state.expiry()))
                Outcome.Ok(id)
            }
        }
    }

    override suspend fun stop(id: SessionId): Outcome<Unit, SessionError> =
        checked("stop", id) { session ->
            if (!session.state.isActive) return@checked Outcome.Ok(Unit)
            runtime.stop(id).then("stop", id) { Expected.Moves(id, session.phase(), RuntimePhase.STOPPED, it) }
        }

    override suspend fun respawn(id: SessionId): Outcome<Unit, SessionError> =
        checked("respawn", id) { session ->
            if (session.origin == SessionOrigin.DISK) return@checked Outcome.Err(SessionError.UnknownSession(id))
            if (session.state.isActive) return@checked Outcome.Err(SessionError.SessionActive(id))
            runtime.respawn(id).then("respawn", id) { Expected.Moves(id, session.phase(), RuntimePhase.WORKING, it) }
        }

    override suspend fun resume(id: SessionId): Outcome<ResumedSession, SessionError> =
        checked("resume", id) { session -> resumeCommand.run(session) }

    override suspend fun remove(id: SessionId): Outcome<Unit, SessionError> =
        checked("remove", id) { session ->
            if (session.origin == SessionOrigin.DISK) return@checked state.hide(id)
            if (session.state.isActive) return@checked Outcome.Err(SessionError.SessionActive(id))
            runtime.remove(id).then("remove", id) { Expected.Disappears(id, it) }
        }

    override suspend fun assignPlace(
        id: SessionId,
        place: PlaceAssignment?,
    ): Outcome<Unit, SessionError> {
        val result = state.assign(id, place)
        val kind = place.kind()
        when (result) {
            is Outcome.Ok -> {
                logger.info("Session place assigned", "session" to id.value, "kind" to kind)
            }

            is Outcome.Err -> {
                logger.warn(
                    "Session place not assigned",
                    "session" to id.value,
                    "kind" to kind,
                    "problem" to result.error::class.simpleName,
                )
            }
        }
        return result
    }

    override suspend fun stopAllIn(project: ProjectId): Outcome<Int, SessionError> {
        if (!dirs.has(project)) return Outcome.Err(SessionError.UnknownProject(project))
        return when (val listed = state.refresh()) {
            is Outcome.Err -> failed("stopAllIn", null, listed.error)

            // Same targets as SessionsSnapshot.countsFor: the project's active sessions, overlay included.
            is Outcome.Ok -> stopAll.run(listed.value.sessions.filter { it.project == project && it.state.isActive })
        }
    }

    /** Runs [run] for [command] on [id] as a fresh listing shows it; a failed listing or an unknown id ends here. */
    private suspend fun <T> checked(
        command: String,
        id: SessionId,
        run: suspend (Session) -> Outcome<T, SessionError>,
    ): Outcome<T, SessionError> =
        when (val listed = state.refresh()) {
            is Outcome.Err -> {
                failed(command, id, listed.error)
            }

            is Outcome.Ok -> {
                listed.value.sessions
                    .firstOrNull { it.id == id }
                    ?.let { run(it) }
                    ?: Outcome.Err(SessionError.UnknownSession(id))
            }
        }

    /** On success refreshes with the effect built for the overlay expiry; maps a failure. */
    private suspend fun Outcome<Unit, RuntimeError>.then(
        command: String,
        id: SessionId,
        effect: (Instant) -> Expected,
    ): Outcome<Unit, SessionError> =
        when (this) {
            is Outcome.Ok -> {
                done(command, id, effect(state.expiry()))
                Outcome.Ok(Unit)
            }

            is Outcome.Err -> {
                failed(command, id, error)
            }
        }

    /** Logs the command and publishes its [expected] effect over a fresh listing. */
    private suspend fun done(
        command: String,
        id: SessionId,
        expected: Expected,
    ) {
        logger.info("Session command done", "command" to command, "session" to id.value)
        state.refresh(listOf(expected))
    }

    private fun <T> failed(
        command: String,
        id: SessionId?,
        error: RuntimeError,
    ): Outcome<T, SessionError> {
        logger.commandFailed(command, id, error)
        return Outcome.Err(error.toSessionError())
    }
}

/** ENG-C8: the command, the session id and the fixed problem only, never the error's details. */
internal fun Logger.commandFailed(
    command: String,
    id: SessionId?,
    error: RuntimeError,
) {
    warn("Session command failed", "command" to command, "session" to id?.value, "problem" to error.toProblem())
}

/** 1:1 with [NewSessionSpec] in [cwd]; never throws, the invariants of both specs are the same. */
internal fun NewSessionSpec.toStartSpec(cwd: Path): StartSpec =
    StartSpec(
        cwd = cwd,
        prompt = prompt,
        name = name,
        agent = agent,
        model = model,
        permissionMode = permissionMode?.toRuntime(),
    )

private fun SessionPermissionMode.toRuntime(): PermissionMode =
    when (this) {
        SessionPermissionMode.DEFAULT -> PermissionMode.DEFAULT
        SessionPermissionMode.ACCEPT_EDITS -> PermissionMode.ACCEPT_EDITS
        SessionPermissionMode.PLAN -> PermissionMode.PLAN
        SessionPermissionMode.BYPASS_PERMISSIONS -> PermissionMode.BYPASS_PERMISSIONS
    }

/** The supervisor phase [Session.state] comes from (inverse of [toSessionState]); the `from` of a move. */
internal fun Session.phase(): RuntimePhase =
    when (state) {
        SessionState.Working -> RuntimePhase.WORKING
        is SessionState.AwaitingInput, is SessionState.AwaitingPermission -> RuntimePhase.BLOCKED
        SessionState.Done -> RuntimePhase.DONE
        SessionState.Failed -> RuntimePhase.FAILED
        SessionState.Stopped -> RuntimePhase.STOPPED
    }

/** ENG-C8: the kind of a place for the logs, never its subfolder name. */
private fun PlaceAssignment?.kind(): String =
    when (this) {
        null -> "auto"
        PlaceAssignment.ProjectRoot -> "root"
        is PlaceAssignment.Subfolder -> "subfolder"
    }
