package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.process.ProcessRunner
import dev.quayside.core.process.RunOptions
import dev.quayside.notifications.api.SoundId
import java.nio.file.Path
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * [SoundPort] on macOS `afplay`, started through [runner] in [workDir] with argv
 * `[program, -v, <volume / 100>, <soundsDir>/<name>.aiff]` and a 10 s timeout. A missing file makes `afplay` exit
 * with a non-zero code; then one more try uses [fallback] (Glass by default), unless the sound already was the fallback or [fallback] is null.
 */
internal class AfplayAdapter(
    private val runner: ProcessRunner,
    private val workDir: Path,
    private val soundsDir: Path = Path.of("/System/Library/Sounds"),
    private val program: String = "/usr/bin/afplay",
    private val fallback: SoundId? = FALLBACK,
) : SoundPort {
    override suspend fun play(
        sound: SoundId,
        volume: Int,
    ): Outcome<Unit, SoundError> {
        val first = run(sound, volume)
        val failed = first is Outcome.Ok && first.value.exitCode != 0
        return toOutcome(if (failed && fallback != null && sound != fallback) run(fallback, volume) else first)
    }

    private suspend fun run(
        sound: SoundId,
        volume: Int,
    ): Outcome<ProcessResult, ProcessError> {
        val file = soundsDir.resolve("${sound.name}.aiff").toString()
        val factor = String.format(Locale.ROOT, "%.2f", volume / PERCENT)
        return runner.run(ShellCommand(listOf(program, "-v", factor, file), workDir), RunOptions(timeout = TIMEOUT))
    }

    private fun toOutcome(result: Outcome<ProcessResult, ProcessError>): Outcome<Unit, SoundError> =
        when (result) {
            is Outcome.Err -> Outcome.Err(failure(result.error))
            is Outcome.Ok -> exitOutcome(result.value.exitCode)
        }

    private fun exitOutcome(code: Int): Outcome<Unit, SoundError> =
        if (code == 0) Outcome.Ok(Unit) else Outcome.Err(SoundError.Failed(code))

    private fun failure(error: ProcessError): SoundError =
        when (error) {
            is ProcessError.Timeout -> SoundError.TimedOut
            is ProcessError.NotFound, is ProcessError.StartFailed, is ProcessError.IoFailed -> SoundError.Unavailable
        }

    internal companion object {
        val FALLBACK = SoundId("Glass")
        private val TIMEOUT = 10.seconds
        private const val PERCENT = 100.0
    }
}
