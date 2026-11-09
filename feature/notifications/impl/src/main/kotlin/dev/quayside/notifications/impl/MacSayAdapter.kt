package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.process.ProcessRunner
import dev.quayside.core.process.RunOptions
import java.nio.file.Path
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * [SpeechPort] on macOS `say`, started through [runner] in [workDir]. The text goes last behind a `[[volm]]`
 * prefix, so a text starting with `-` is never read as an option. When `say` fails with a chosen voice (it may
 * not be installed), one more try uses the system voice.
 */
internal class MacSayAdapter(
    private val runner: ProcessRunner,
    private val workDir: Path,
    private val program: String = "/usr/bin/say",
    private val fallbackToSystemVoice: Boolean = true,
) : SpeechPort {
    override suspend fun speak(utterance: Utterance): Outcome<Unit, SpeechError> {
        val first = run(utterance)
        val retry =
            fallbackToSystemVoice && first is Outcome.Ok && first.value.exitCode != 0 && utterance.voice != null
        return if (retry) toOutcome(run(utterance.copy(voice = null))) else toOutcome(first)
    }

    private suspend fun run(utterance: Utterance): Outcome<ProcessResult, ProcessError> =
        runner.run(ShellCommand(argv(utterance), workDir), RunOptions(timeout = TIMEOUT))

    private fun argv(utterance: Utterance): List<String> {
        val volume = String.format(Locale.ROOT, "%.2f", utterance.volume / PERCENT)
        return listOf(program) +
            utterance.voice?.let { listOf("-v", it.name) }.orEmpty() +
            listOf("-r", utterance.rate.toString(), "[[volm $volume]] ${utterance.text}")
    }

    private fun toOutcome(result: Outcome<ProcessResult, ProcessError>): Outcome<Unit, SpeechError> =
        when (result) {
            is Outcome.Err -> {
                Outcome.Err(failure(result.error))
            }

            is Outcome.Ok -> {
                val code = result.value.exitCode
                if (code == 0) Outcome.Ok(Unit) else Outcome.Err(SpeechError.Failed(code))
            }
        }

    private fun failure(error: ProcessError): SpeechError =
        when (error) {
            is ProcessError.Timeout -> SpeechError.TimedOut
            is ProcessError.NotFound, is ProcessError.StartFailed, is ProcessError.IoFailed -> SpeechError.Unavailable
        }

    private companion object {
        val TIMEOUT = 60.seconds
        const val PERCENT = 100.0
    }
}
