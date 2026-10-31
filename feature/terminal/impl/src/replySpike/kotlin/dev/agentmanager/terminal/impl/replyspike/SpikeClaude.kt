package dev.agentmanager.terminal.impl.replyspike

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.domain.flatMap
import dev.agentmanager.core.domain.map
import dev.agentmanager.core.domain.mapError
import dev.agentmanager.core.process.ProcessError
import dev.agentmanager.core.process.ProcessRunner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path

/** Fixed error categories; never output text (ENG-C8). */
internal enum class SpikeError { NOT_FOUND, TIMEOUT, START_FAILED, IO_FAILED, COMMAND_FAILED, UNPARSEABLE }

/** Phase of a background session as the spike needs it. */
internal enum class SpikePhase { WORKING, BLOCKED, DONE, FAILED, STOPPED, OTHER }

/** Session from `agents --json --all`; [waitingFor] is the raw token (e.g. "permission prompt"). */
internal data class SpikeSession(
    val id: String,
    val phase: SpikePhase,
    val waitingFor: String?,
)

/**
 * `claude` CLI for the runner over [ProcessRunner]; errors are fixed categories, never output text (ENG-C8).
 * Commands without a directory of their own (`agents`, `stop`, `rm`) run in [workDir].
 */
internal class SpikeClaude(
    private val runner: ProcessRunner,
    private val executable: String = "claude",
    private val workDir: Path = Path.of(System.getProperty("java.io.tmpdir")),
) {
    suspend fun start(
        name: String,
        prompt: String,
        dir: Path,
        model: String = "haiku",
    ): Outcome<Unit, SpikeError> = exec(listOf("--bg", "--name", name, "--model", model, "--", prompt), dir).map { }

    /** Output of `claude --version`, recorded in the report. */
    suspend fun version(): Outcome<String, SpikeError> = exec(listOf("--version"), workDir).map { it.trim() }

    /** The background session called [name] (id, phase, waitingFor from `agents --json --all`); null when unknown. */
    suspend fun find(name: String): Outcome<SpikeSession?, SpikeError> = sessions(name).map { it.firstOrNull() }

    /** All background sessions called [name]; more than one means the attach started a duplicate. */
    suspend fun sessions(name: String): Outcome<List<SpikeSession>, SpikeError> =
        exec(listOf("agents", "--json", "--all"), workDir).flatMap { parse(it, name) }

    /**
     * Stops the session, then removes it. A failing `stop` (e.g. it already ended) does not skip `rm`, so every
     * session is cleaned up; the first error is reported.
     */
    suspend fun stopAndRemove(id: String): Outcome<Unit, SpikeError> {
        val stopped = exec(listOf("stop", id), workDir)
        val removed = exec(listOf("rm", id), workDir)
        return if (stopped is Outcome.Err) Outcome.Err(stopped.error) else removed.map { }
    }

    private suspend fun exec(
        args: List<String>,
        dir: Path,
    ): Outcome<String, SpikeError> =
        runner
            .run(ShellCommand(listOf(executable) + args, dir))
            .mapError(::category)
            .flatMap { if (it.isSuccess) Outcome.Ok(it.stdout) else Outcome.Err(SpikeError.COMMAND_FAILED) }

    private fun category(error: ProcessError): SpikeError =
        when (error) {
            is ProcessError.NotFound -> SpikeError.NOT_FOUND
            is ProcessError.Timeout -> SpikeError.TIMEOUT
            is ProcessError.StartFailed -> SpikeError.START_FAILED
            is ProcessError.IoFailed -> SpikeError.IO_FAILED
        }
}

// Exception messages of kotlinx.serialization quote the input, so the exception is dropped.
private fun parse(
    stdout: String,
    name: String,
): Outcome<List<SpikeSession>, SpikeError> {
    val tree: JsonElement? =
        try {
            Json.parseToJsonElement(stdout)
        } catch (ignored: IllegalArgumentException) {
            null
        }
    return if (tree is JsonArray) {
        Outcome.Ok(tree.filterIsInstance<JsonObject>().filter { it.text("name") == name }.mapNotNull(::session))
    } else {
        Outcome.Err(SpikeError.UNPARSEABLE)
    }
}

private fun session(entry: JsonObject): SpikeSession? {
    val id = entry.text("id")
    val background = entry.text("kind") != "interactive"
    val waitingFor = entry.text("waitingFor")
    val phase = phase(entry.text("state") ?: entry.text("status"), waitingFor)
    return if (id != null && background) SpikeSession(id, phase, waitingFor) else null
}

// State tokens follow AgentsJsonParser (feature/agent-runtime): unknown or missing state follows waitingFor.
// The rule DONE + waitingFor input/idle_prompt -> BLOCKED in phase() goes beyond that parser: it is a spike
// extension for text questions, to be verified in step 1 (`record` + `agents --json`). Risk: STILL_BLOCKED after
// a successful reply, if a finished session also reports idle_prompt.
private val PHASES: Map<String, SpikePhase> =
    mapOf(
        SpikePhase.WORKING to listOf("working", "busy", "running", "active"),
        SpikePhase.BLOCKED to listOf("blocked", "waiting", "needs_input"),
        SpikePhase.DONE to listOf("done", "idle", "completed", "finished"),
        SpikePhase.FAILED to listOf("failed", "error", "errored", "crashed"),
        SpikePhase.STOPPED to listOf("stopped", "exited", "killed", "terminated"),
    ).flatMap { (phase, tokens) -> tokens.map { it to phase } }.toMap()

private val INPUT_WAIT = setOf("input", "input_needed", "idle_prompt", "agent_needs_input")

private fun phase(
    raw: String?,
    waitingFor: String?,
): SpikePhase {
    val known = raw?.let { PHASES[normalize(it)] }
    val needsInput = waitingFor != null && normalize(waitingFor) in INPUT_WAIT
    return when {
        known == null -> if (waitingFor != null) SpikePhase.BLOCKED else SpikePhase.WORKING
        known == SpikePhase.DONE && needsInput -> SpikePhase.BLOCKED
        else -> known
    }
}

private fun normalize(token: String): String =
    token
        .trim()
        .lowercase()
        .replace('-', '_')
        .replace(' ', '_')

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
