package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.process.ProcessRunner
import dev.quayside.core.process.RunOptions
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * [SoundPort] on macOS `afplay`, started through [runner] in [workDir] with argv `[program, <soundsDir>/<file>.aiff]`
 * and a 10 s timeout. A missing file makes `afplay` exit with a non-zero code; then one more try uses [FALLBACK]
 * (Glass), unless the sound already was the fallback.
 */
internal class AfplayAdapter(
    private val runner: ProcessRunner,
    private val workDir: Path,
    private val soundsDir: Path = Path.of("/System/Library/Sounds"),
    private val program: String = "/usr/bin/afplay",
) : SoundPort {
    override suspend fun play(sound: SystemSound): Outcome<Unit, SoundError> {
        val first = run(sound)
        val retry = first is Outcome.Ok && first.value.exitCode != 0 && sound != FALLBACK
        return toOutcome(if (retry) run(FALLBACK) else first)
    }

    private suspend fun run(sound: SystemSound): Outcome<ProcessResult, ProcessError> {
        val file = soundsDir.resolve("${sound.fileName}.aiff").toString()
        return runner.run(ShellCommand(listOf(program, file), workDir), RunOptions(timeout = TIMEOUT))
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
        val FALLBACK = SystemSound.GLASS
        private val TIMEOUT = 10.seconds
    }
}
