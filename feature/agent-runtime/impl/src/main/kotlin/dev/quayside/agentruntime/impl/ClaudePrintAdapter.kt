package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.OneShotPrompt
import dev.quayside.agentruntime.api.PromptRequest
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.flatMap
import dev.quayside.core.process.ProcessRunner
import java.nio.file.Path

/**
 * [OneShotPrompt] over `claude -p` (ADR 0019): runs in [homeDir], not in a project, and never logs the command,
 * because its argv holds the prompt.
 */
internal class ClaudePrintAdapter(
    private val process: ProcessRunner,
    private val homeDir: Path,
    private val executable: String,
) : OneShotPrompt {
    override suspend fun ask(request: PromptRequest): Outcome<String, RuntimeError> =
        process
            .exec(ClaudeCommands.print(executable, request), homeDir, request.timeout, MAX_OUTPUT_BYTES)
            .flatMap { result ->
                if (result.isSuccess) {
                    PrintResultParser.parse(result.stdout, result.exitCode)
                } else {
                    Outcome.Err(commandFailed(result, secrets = listOf(request.prompt)))
                }
            }

    private companion object {
        const val MAX_OUTPUT_BYTES = 256 * 1024
    }
}
