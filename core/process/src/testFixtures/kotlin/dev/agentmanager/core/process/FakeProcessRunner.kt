package dev.agentmanager.core.process

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ShellCommand

/**
 * Scripted [ProcessRunner] for tests of code that runs processes (ENG-I7). Every call is recorded in
 * [calls]; [respond] decides the result. By default every command succeeds with empty output.
 */
public class FakeProcessRunner(
    private val respond: (ShellCommand) -> Outcome<ProcessResult, ProcessError> = {
        Outcome.Ok(ProcessResult(0, "", ""))
    },
) : ProcessRunner {
    private val recorded = mutableListOf<Pair<ShellCommand, RunOptions>>()

    /** Commands with the options they were run with, in call order. */
    public val calls: List<Pair<ShellCommand, RunOptions>> get() = synchronized(recorded) { recorded.toList() }

    /** Just the commands, in call order. */
    public val commands: List<ShellCommand> get() = calls.map { it.first }

    override suspend fun run(
        command: ShellCommand,
        options: RunOptions,
    ): Outcome<ProcessResult, ProcessError> {
        synchronized(recorded) { recorded += command to options }
        return respond(command)
    }
}
