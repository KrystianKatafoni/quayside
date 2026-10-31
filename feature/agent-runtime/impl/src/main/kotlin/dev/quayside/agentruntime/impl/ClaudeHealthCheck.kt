package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeHealth
import dev.quayside.agentruntime.api.RuntimeRequirements
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.debug
import dev.quayside.core.domain.fold
import dev.quayside.core.domain.getOrElse
import dev.quayside.core.domain.info
import dev.quayside.core.process.ProcessRunner

/** `health()` of [ClaudeCliAdapter]; see docs/plans/AM-204.md. */
internal class ClaudeHealthCheck(
    private val process: ProcessRunner,
    private val logger: Logger,
    private val config: CliConfig,
) {
    private val exe: String get() = config.executable

    /**
     * `--version`, then `daemon status`, then `agents --json`, one after another (docs/plans/AM-204.md).
     * A missing, failing, unreadable or too old version ends the check; the daemon state is informative
     * only, because the supervisor starts on demand. Logs one INFO entry without any command output.
     */
    suspend fun run(): RuntimeHealth {
        val health = checkVersion().fold(onOk = { checkSupervisorAndAgentView(it) }, onErr = { it })
        logger.info(
            "claude health",
            "version" to health.version,
            "agentViewEnabled" to health.agentViewEnabled,
            "supervisorRunning" to health.supervisorRunning,
            "problems" to health.problems.map { it.category() },
        )
        return health
    }

    /** The supported version, or the final health when the check cannot go on. */
    private suspend fun checkVersion(): Outcome<ClaudeVersion, RuntimeHealth> {
        val ran = process.exec(ClaudeCommands.version(exe), config.homeDir, config.commandTimeout)
        val result = ran.getOrElse { return Outcome.Err(notReady(null, it)) }
        val version = ClaudeVersion.parse(result.stdout)
        return when {
            !result.isSuccess -> Outcome.Err(notReady(null, commandFailed(result, secrets = emptyList())))
            version == null -> Outcome.Err(notReady(null, RuntimeError.Unparseable(VERSION_UNPARSEABLE)))
            version < ClaudeVersion.MINIMUM -> Outcome.Err(notReady(version.toString(), unsupported(version)))
            else -> Outcome.Ok(version)
        }
    }

    /** `daemon status` first, since `agents` may start the supervisor itself. */
    private suspend fun checkSupervisorAndAgentView(version: ClaudeVersion): RuntimeHealth {
        val supervisorRunning = isSupervisorRunning()
        val agentViewProblem = agentViewProblem()
        return RuntimeHealth(
            version = version.toString(),
            agentViewEnabled = agentViewProblem == null,
            supervisorRunning = supervisorRunning,
            problems = listOfNotNull(agentViewProblem),
        )
    }

    private suspend fun isSupervisorRunning(): Boolean =
        process.exec(ClaudeCommands.daemonStatus(exe), config.homeDir, config.commandTimeout).fold(
            onOk = { DaemonStatus.isRunning(it.exitCode, it.stdout) },
            onErr = { error ->
                logger.debug("claude daemon status failed", "problem" to error.category())
                false
            },
        )

    /** Null when Agent View answers. */
    private suspend fun agentViewProblem(): RuntimeError? =
        process.exec(ClaudeCommands.agentViewProbe(exe), config.homeDir, config.commandTimeout).fold(
            onOk = { result ->
                when {
                    result.isSuccess -> null
                    AgentViewProbe.isDisabledMessage(result.stderr, result.stdout) -> RuntimeError.AgentViewDisabled
                    else -> commandFailed(result, secrets = emptyList())
                }
            },
            onErr = { it },
        )
}

private const val VERSION_UNPARSEABLE = "version:unparseable"

private fun notReady(
    version: String?,
    problem: RuntimeError,
): RuntimeHealth =
    RuntimeHealth(version, agentViewEnabled = false, supervisorRunning = false, problems = listOf(problem))

private fun unsupported(version: ClaudeVersion): RuntimeError =
    RuntimeError.UnsupportedVersion(found = version.toString(), required = RuntimeRequirements.MIN_CLAUDE_VERSION)

/** Class name only: the error may carry a stderr tail, which never goes to the log. */
private fun RuntimeError.category(): String = this::class.simpleName ?: "RuntimeError"
