package dev.quayside.core.process

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.domain.catching
import dev.quayside.core.domain.debug
import dev.quayside.core.domain.flatMap
import dev.quayside.core.domain.warn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [ProcessRunner] that starts every command as `<shell> -l -c 'exec "$@"' quayside <argv>`.
 *
 * A macOS app launched from Finder gets a minimal `PATH`; the login shell loads the user's profile
 * (`.zprofile` for zsh), so programs such as `claude` are found as in Terminal. Arguments are passed
 * as positional parameters, never re-parsed by the shell. On timeout or cancellation the process and
 * all its descendants are killed. Blocking calls run on [io].
 *
 * The result holds the output written until the process exited plus at most one more second (shared
 * by stdout and stderr), so the call never waits for a background child that keeps the pipes open.
 * Such children (for example the service started by `claude --bg`) are not killed after a normal
 * exit. Once the process has exited, the JDK closes its pipes, so later writes of a child to the
 * inherited stdout or stderr may fail with EPIPE/SIGPIPE: a process meant to outlive the call must
 * detach its stdio (see [StreamDrain]).
 *
 * @param io dispatcher for the blocking process calls, usually `AppDispatchers.io`.
 * @param shell login shell to start; defaults to [defaultShell].
 * @param logger receives the program name and working directory, never arguments or environment.
 */
public class LoginShellProcessRunner(
    private val io: CoroutineDispatcher,
    private val shell: Path = defaultShell(),
    private val logger: Logger = NoOpLogger,
) : ProcessRunner {
    override suspend fun run(
        command: ShellCommand,
        options: RunOptions,
    ): Outcome<ProcessResult, ProcessError> =
        withContext(io) {
            logger.debug("process start", "program" to command.argv.first(), "workDir" to command.workDir)
            when (val started = start(command)) {
                is Outcome.Err -> Outcome.Err(started.error)
                is Outcome.Ok -> catching(::ioFailed) { collect(started.value, command, options) }.flatMap { it }
            }
        }

    private fun start(command: ShellCommand): Outcome<Process, ProcessError> =
        catching({ ProcessError.StartFailed(it.describe()) }) {
            ProcessBuilder(listOf(shell.toString(), "-l", "-c", EXEC_ARGS, ARG0) + command.argv)
                .directory(command.workDir.toFile())
                .apply { environment().putAll(command.env) }
                .start()
                .also { it.outputStream.close() }
        }

    private suspend fun collect(
        process: Process,
        command: ShellCommand,
        options: RunOptions,
    ): Outcome<ProcessResult, ProcessError> {
        try {
            val stdout = StreamDrain(process.inputStream, options.maxOutputBytes)
            val stderr = StreamDrain(process.errorStream, options.maxOutputBytes)
            val exited = runInterruptible { process.waitFor(options.timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS) }
            // Timeout: killing the tree closes the pipes, so the readers reach EOF at once.
            if (!exited) kill(process)
            // A background child may keep the pipes open long after the exit, so the wait is bounded.
            withTimeoutOrNull(DRAIN_AFTER_EXIT) {
                stdout.awaitEof()
                stderr.awaitEof()
            }
            val exitCode = if (exited) process.exitValue() else null
            return result(command.argv.first(), options.timeout, exitCode, stdout.take(), stderr.take())
        } finally {
            // Cancellation, or a reader that failed to start: the tree must not outlive the call.
            if (process.isAlive) kill(process)
        }
    }

    /** [exitCode] is null when the process was killed after [timeout]. */
    private fun result(
        program: String,
        timeout: Duration,
        exitCode: Int?,
        out: Captured,
        err: Captured,
    ): Outcome<ProcessResult, ProcessError> =
        when {
            exitCode == null -> {
                logger.warn("process timeout", "program" to program, "timeout" to timeout)
                Outcome.Err(ProcessError.Timeout(timeout))
            }

            isNotFound(exitCode, err.text, program) -> {
                Outcome.Err(ProcessError.NotFound(program))
            }

            else -> {
                Outcome.Ok(ProcessResult(exitCode, out.text, err.text, out.truncated, err.truncated))
            }
        }

    /** SIGKILLs the tree; the wait is bounded so that a stuck reap cannot hang the caller. */
    private fun kill(process: Process) {
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
        process.waitFor(KILL_WAIT.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }

    private fun ioFailed(e: Exception): ProcessError = ProcessError.IoFailed(e.describe())

    private fun Exception.describe(): String = message ?: javaClass.simpleName

    public companion object {
        private const val EXEC_ARGS = "exec \"\$@\""
        private const val ARG0 = "quayside"
        private const val NOT_FOUND_EXIT = 127
        private const val NOT_FOUND_TEXT = "not found"

        /** How long both readers together may still take after the exit; output arriving later is dropped. */
        private val DRAIN_AFTER_EXIT = 1.seconds

        /** How long a killed process may take to be reaped. */
        private val KILL_WAIT = 1.seconds

        /** Shell exit 127 with its own "not found" line naming the program, not a profile error. */
        internal fun isNotFound(
            exitCode: Int,
            stderr: String,
            program: String,
        ): Boolean = exitCode == NOT_FOUND_EXIT && stderr.lines().any { NOT_FOUND_TEXT in it && program in it }

        /** The user's shell from `SHELL`, or `/bin/zsh` (the macOS default) when it is not set. */
        public fun defaultShell(): Path =
            System.getenv("SHELL")?.takeIf { it.isNotBlank() }?.let { Path.of(it) } ?: Path.of("/bin/zsh")
    }
}

internal class Captured(
    val text: String,
    val truncated: Boolean,
)

private const val BUFFER_SIZE = 8192

/** Thread-safe sink that keeps at most [limit] bytes from the start of a stream. */
internal class OutputCapture(
    private val limit: Int,
) {
    private var kept = ByteArrayOutputStream()
    private var truncated = false
    private var sealed = false

    @Synchronized
    fun append(
        buffer: ByteArray,
        count: Int,
    ) {
        if (sealed) return
        val room = limit - kept.size()
        if (room > 0) kept.write(buffer, 0, minOf(count, room))
        if (count > room) truncated = true
    }

    @Synchronized
    fun snapshot(): Captured = Captured(kept.toString(Charsets.UTF_8), truncated)

    /** Returns the [snapshot], frees the buffer and drops every later [append]. */
    @Synchronized
    fun take(): Captured =
        snapshot().also {
            sealed = true
            kept = ByteArrayOutputStream(0)
        }
}

/** Reads the stream to the end into [capture]. */
internal fun InputStream.readInto(capture: OutputCapture) {
    val buffer = ByteArray(BUFFER_SIZE)
    var read = read(buffer)
    while (read >= 0) {
        capture.append(buffer, read)
        read = read(buffer)
    }
}

/** Reads the stream to the end, keeping at most [limit] bytes from its start. */
internal fun InputStream.readCapped(limit: Int): Captured =
    use { stream -> OutputCapture(limit).also { stream.readInto(it) }.snapshot() }

private val drainThreads = AtomicInteger()

/**
 * Owns [stream] and reads it to EOF on a dedicated daemon thread `am-pipe-drain-<n>`.
 *
 * This is a deliberate exception to structured concurrency (ENG-C6): a blocking `read()` cannot be
 * cancelled (closing the stream does not unblock it), and the pipe stays open until the JDK closes it
 * after the process exit or until the last background child holding it ends. The caller stops waiting
 * after a bound and [take]s what was read; the thread ends on its own at EOF or on a read failure.
 * A thread of its own, not `Dispatchers.IO`, so such readers never occupy the shared pool meanwhile.
 *
 * It does not keep background children writable: after the process exit the JDK closes the pipe, so
 * their later writes to the inherited stdout/stderr may get EPIPE/SIGPIPE.
 */
private class StreamDrain(
    stream: InputStream,
    limit: Int,
) {
    private val capture = OutputCapture(limit)
    private val eof = CompletableDeferred<Unit>()

    init {
        val reader = Thread({ drain(stream) }, "am-pipe-drain-${drainThreads.incrementAndGet()}")
        reader.isDaemon = true
        var started = false
        try {
            reader.start()
            started = true
        } finally {
            if (!started) stream.closeQuietly()
        }
    }

    // Any failure must complete [eof], or the caller would wait for the full bound for nothing.
    @Suppress("TooGenericExceptionCaught")
    private fun drain(stream: InputStream) {
        try {
            stream.use { it.readInto(capture) }
            eof.complete(Unit)
        } catch (e: Exception) {
            eof.completeExceptionally(e)
        }
    }

    /** Suspends until EOF; rethrows a read failure. */
    suspend fun awaitEof() = eof.await()

    /** Output read so far, at most the limit; everything read later is discarded. */
    fun take(): Captured = capture.take()
}

private fun InputStream.closeQuietly() {
    try {
        close()
    } catch (ignored: IOException) {
        // Only releases the descriptor of a stream nobody reads.
    }
}
