package dev.agentmanager.agentruntime.impl

import dev.agentmanager.agentruntime.api.AgentRuntime
import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimeHealth
import dev.agentmanager.agentruntime.api.RuntimeSession
import dev.agentmanager.agentruntime.api.StartSpec
import dev.agentmanager.core.domain.Clock
import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.domain.debug
import dev.agentmanager.core.domain.flatMap
import dev.agentmanager.core.domain.map
import dev.agentmanager.core.domain.mapError
import dev.agentmanager.core.domain.warn
import dev.agentmanager.core.process.ProcessError
import dev.agentmanager.core.process.ProcessResult
import dev.agentmanager.core.process.ProcessRunner
import dev.agentmanager.core.process.RunOptions
import java.nio.file.Path
import kotlin.time.Duration

/**
 * Settings of [ClaudeCliAdapter], see `agentRuntimeModule`.
 *
 * @property homeDir working directory of every command except `start`, and of `attachCommand`.
 * @property executable `claude` on the login-shell PATH, or an absolute path.
 * @property commandTimeout limit of `agents`, `logs`, `stop`, `respawn`, `rm` and of each health command.
 * @property startTimeout limit of `--bg`.
 */
internal data class CliConfig(
    val homeDir: Path,
    val executable: String,
    val commandTimeout: Duration,
    val startTimeout: Duration,
)

/** [AgentRuntime] over the `claude` CLI (Agent View, ADR 0002); see docs/plans/AM-203.md. */
internal class ClaudeCliAdapter(
    private val process: ProcessRunner,
    private val clock: Clock,
    private val logger: Logger,
    private val config: CliConfig,
) : AgentRuntime {
    private val exe: String get() = config.executable
    private val healthCheck = ClaudeHealthCheck(process, logger, config)

    /**
     * Filters on the client by path components ([isUnder]); [under] must be a real path (no symbolic
     * links, e.g. from `toRealPath()`), because the CLI reports the real `cwd` of each session.
     */
    override suspend fun listSessions(under: Path?): Outcome<List<RuntimeSession>, RuntimeError> =
        list().map { sessions -> if (under == null) sessions else sessions.filter { it.isUnder(under) } }

    override suspend fun start(spec: StartSpec): Outcome<SessionId, RuntimeError> =
        process.exec(ClaudeCommands.start(exe, spec), spec.cwd, config.startTimeout).flatMap { result ->
            val id = BgOutput.sessionId(result.stdout)
            when {
                !result.isSuccess -> Outcome.Err(commandFailed(result, secrets = listOf(spec.prompt)))
                id == null -> Outcome.Err(RuntimeError.Unparseable(NO_ID))
                else -> Outcome.Ok(id).also { logger.debug("claude started", "sessionId" to id.value) }
            }
        }

    override suspend fun stop(id: SessionId): Outcome<Unit, RuntimeError> = byId("stop", id).map { }

    override suspend fun respawn(id: SessionId): Outcome<Unit, RuntimeError> = byId("respawn", id).map { }

    override suspend fun remove(id: SessionId): Outcome<Unit, RuntimeError> =
        byId("rm", id).flatMap { result ->
            // `rm` refuses a worktree with unsaved work by printing `kept <id>`, possibly on stdout.
            val output = listOf(result.stderr, result.stdout).filter { it.isNotBlank() }.joinToString("\n")
            val kept = output.lineSequence().any { it.trim().startsWith("kept ${id.value}") }
            if (kept) {
                Outcome.Err(RuntimeError.CommandFailed(result.exitCode, StderrTail.of(output, emptyList())))
            } else {
                Outcome.Ok(Unit)
            }
        }

    override suspend fun recentOutput(id: SessionId): Outcome<String, RuntimeError> =
        byId("logs", id, LOGS_MAX_BYTES).map { result ->
            if (result.stdoutTruncated) logger.warn("claude logs truncated", "sessionId" to id.value)
            OutputTail.of(result.stdout)
        }

    override fun attachCommand(id: SessionId): ShellCommand =
        ShellCommand(ClaudeCommands.byId(exe, "attach", id), config.homeDir)

    /** See [ClaudeHealthCheck.run]: version, then supervisor, then Agent View. */
    override suspend fun health(): RuntimeHealth = healthCheck.run()

    private suspend fun list(): Outcome<List<RuntimeSession>, RuntimeError> =
        process.exec(ClaudeCommands.list(exe), config.homeDir, config.commandTimeout).flatMap { result ->
            if (!result.isSuccess) {
                Outcome.Err(commandFailed(result, secrets = emptyList()))
            } else {
                AgentsJsonParser.parse(result.stdout, clock.now()).map { listing ->
                    logger.logNotices(listing.notices)
                    listing.sessions
                }
            }
        }

    /** Runs `claude <verb> <id>`; a failure is [RuntimeError.UnknownSession] when the id is not listed. */
    private suspend fun byId(
        verb: String,
        id: SessionId,
        maxOutputBytes: Int = DEFAULT_MAX_BYTES,
    ): Outcome<ProcessResult, RuntimeError> {
        logger.debug("claude command", "verb" to verb, "sessionId" to id.value)
        val ran =
            process.exec(
                ClaudeCommands.byId(exe, verb, id),
                config.homeDir,
                config.commandTimeout,
                maxOutputBytes,
            )
        val result = (ran as? Outcome.Ok)?.value
        return when {
            result == null || result.isSuccess -> ran
            isListed(id) == false -> Outcome.Err(RuntimeError.UnknownSession(id))
            else -> Outcome.Err(commandFailed(result, secrets = emptyList()))
        }
    }

    /** Null when the list itself is not available. */
    private suspend fun isListed(id: SessionId): Boolean? = (list() as? Outcome.Ok)?.value?.any { it.id == id }
}

private const val NO_ID = "bg-output:no-id"
private const val DEFAULT_MAX_BYTES = 1024 * 1024
private const val LOGS_MAX_BYTES = 4 * 1024 * 1024

/** Runs [argv] in [workDir]; a process failure becomes a [RuntimeError] without paths. */
internal suspend fun ProcessRunner.exec(
    argv: List<String>,
    workDir: Path,
    timeout: Duration,
    maxOutputBytes: Int = DEFAULT_MAX_BYTES,
): Outcome<ProcessResult, RuntimeError> =
    run(ShellCommand(argv, workDir), RunOptions(timeout, maxOutputBytes)).mapError(::toRuntimeError)

private fun toRuntimeError(error: ProcessError): RuntimeError =
    when (error) {
        is ProcessError.NotFound -> RuntimeError.NotInstalled

        is ProcessError.Timeout -> RuntimeError.Timeout

        // The reason may contain paths, so only a fixed category is kept.
        is ProcessError.StartFailed -> RuntimeError.CommandFailed(-1, "process:start-failed")

        is ProcessError.IoFailed -> RuntimeError.CommandFailed(-1, "process:io-failed")
    }

/** [RuntimeError.CommandFailed] with the redacted stderr tail of [result]. */
internal fun commandFailed(
    result: ProcessResult,
    secrets: List<String>,
): RuntimeError = RuntimeError.CommandFailed(result.exitCode, StderrTail.of(result.stderr, secrets))

/** Only categories and counts: notices may name sessions, but never carry input text here. */
private fun Logger.logNotices(notices: List<ParseNotice>) {
    if (notices.isEmpty()) return
    log(LogLevel.DEBUG, "agents json notices", notices.groupingBy { it.category() }.eachCount())
}

private fun ParseNotice.category(): String =
    when (this) {
        is ParseNotice.EntrySkipped -> "skipped:${reason.name.lowercase()}"
        is ParseNotice.UnknownPhase -> "unknown-phase"
        is ParseNotice.StartedAtMissing -> "started-at-missing"
    }
