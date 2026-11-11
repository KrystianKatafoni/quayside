package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.debug
import dev.quayside.core.domain.flatMap
import dev.quayside.core.process.ProcessRunner
import java.nio.file.Path

private const val NO_ID = "bg-output:no-id"

/**
 * Runs a `claude --bg` command (`start`, `resume`) in a directory with the hook token and reads the id of the new job
 * from `backgrounded · <id>`. stderr text is never logged; a failure carries the redacted tail only (ENG-C8).
 */
internal class BackgroundStart(
    private val process: ProcessRunner,
    private val logger: Logger,
    private val config: CliConfig,
) {
    /** [secrets] are masked in the stderr tail of a failure (the prompt of a `start`). */
    suspend fun run(
        argv: List<String>,
        cwd: Path,
        secrets: List<String>,
    ): Outcome<SessionId, RuntimeError> {
        val env = config.hookTokens.hookEnv(logger)
        return process.exec(argv, cwd, config.startTimeout, env = env).flatMap { result ->
            val id = BgOutput.sessionId(result.stdout)
            when {
                !result.isSuccess && BgOutput.untrusted(result.stderr) -> Outcome.Err(RuntimeError.WorkspaceNotTrusted)
                !result.isSuccess -> Outcome.Err(commandFailed(result, secrets))
                id == null -> Outcome.Err(RuntimeError.Unparseable(NO_ID))
                else -> Outcome.Ok(id).also { logger.debug("claude started", "sessionId" to id.value) }
            }
        }
    }
}
