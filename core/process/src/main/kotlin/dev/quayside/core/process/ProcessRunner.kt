package dev.quayside.core.process

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Port for running an external program to completion and capturing its output.
 *
 * Implementations never throw for process failures: a non-zero exit code is a successful
 * [ProcessResult], problems with starting or finishing the process are a [ProcessError].
 * Safe to call from any dispatcher; implementations move blocking work to their own IO dispatcher.
 * Cancelling the caller kills the process. Background children that outlive the main process are not
 * killed, and output they write after it exits is not captured.
 */
public interface ProcessRunner {
    /**
     * Runs [command] to completion within the limits of [options].
     *
     * @return [ProcessResult] for any exit code, or [ProcessError.NotFound], [ProcessError.Timeout],
     *   [ProcessError.StartFailed] or [ProcessError.IoFailed].
     */
    public suspend fun run(
        command: ShellCommand,
        options: RunOptions = RunOptions(),
    ): Outcome<ProcessResult, ProcessError>
}

/**
 * Limits for one run. The process is killed after [timeout]; at most [maxOutputBytes] of stdout and
 * of stderr are kept (each, from the start of the stream), the rest is read and discarded so the
 * process never blocks on a full pipe. Defaults: 30 seconds and 1 MiB.
 *
 * @throws IllegalArgumentException when [timeout] is shorter than one millisecond or [maxOutputBytes]
 *   is not positive.
 */
public data class RunOptions(
    val timeout: Duration = DEFAULT_TIMEOUT,
    val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
) {
    init {
        require(timeout >= 1.milliseconds) { "timeout must be at least 1 ms" }
        require(maxOutputBytes > 0) { "maxOutputBytes must be positive" }
    }

    private companion object {
        val DEFAULT_TIMEOUT = 30.seconds
        const val DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024
    }
}

/** Outcome of a process that ran to completion. Output is decoded as UTF-8. */
public data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val stdoutTruncated: Boolean = false,
    val stderrTruncated: Boolean = false,
) {
    /** `true` for exit code 0. */
    val isSuccess: Boolean get() = exitCode == 0
}

/** Why a process produced no [ProcessResult]. */
public sealed interface ProcessError {
    /**
     * The login shell could not find [program] on its `PATH` (shell exit code 127 with its "not found"
     * message). A path to a missing file is not detected and comes back as a result with exit code 127.
     */
    public data class NotFound(
        val program: String,
    ) : ProcessError

    /** The process ran longer than the allowed timeout and was killed together with its children. */
    public data class Timeout(
        val after: Duration,
    ) : ProcessError

    /** The shell itself could not be started, e.g. a missing working directory. */
    public data class StartFailed(
        val reason: String,
    ) : ProcessError

    /** Reading the output of a started process failed; the process was killed. */
    public data class IoFailed(
        val reason: String,
    ) : ProcessError
}
