package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.ProcessRunner
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.TemplateProblem
import dev.quayside.notifications.api.VoicePreview
import dev.quayside.notifications.api.VoicePreviewError
import dev.quayside.notifications.api.VoiceSample
import java.nio.file.Path

/**
 * [VoicePreview] over [MacSayAdapter] without the system-voice retry. A text with `[[` or `]]` is refused before a
 * process starts; a long text is played (a sample of a foreign voice may exceed the template limit).
 */
internal class SayVoicePreview(
    runner: ProcessRunner,
    workDir: Path,
    program: String = "/usr/bin/say",
) : VoicePreview {
    private val speech = MacSayAdapter(runner, workDir, program, fallbackToSystemVoice = false)

    override suspend fun play(sample: VoiceSample): Outcome<Unit, VoicePreviewError> =
        if (SpeechSettings.templateProblem(sample.text) == TemplateProblem.EMBEDDED_COMMAND) {
            Outcome.Err(VoicePreviewError.FAILED)
        } else {
            when (val result = speech.speak(Utterance(sample.text, sample.voice, sample.rate, sample.volume))) {
                is Outcome.Ok -> Outcome.Ok(Unit)
                is Outcome.Err -> Outcome.Err(error(result.error))
            }
        }

    private fun error(error: SpeechError): VoicePreviewError =
        when (error) {
            SpeechError.Unavailable -> VoicePreviewError.UNAVAILABLE
            is SpeechError.Failed, SpeechError.TimedOut -> VoicePreviewError.FAILED
        }
}
