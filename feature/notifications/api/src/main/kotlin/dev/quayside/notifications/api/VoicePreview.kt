package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome

/**
 * One sample to say: [text] in [voice] (null = system voice) at [rate] words per minute and [volume] percent.
 * `toString` hides [text].
 *
 * @throws IllegalArgumentException when [rate] or [volume] is outside the ranges of [SpeechSettings] (a programmer
 *   error; values typed by the user are checked by the caller first).
 */
public data class VoiceSample(
    val voice: VoiceId?,
    val text: String,
    val rate: Int,
    val volume: Int,
) {
    init {
        require(rate in SpeechSettings.MIN_RATE..SpeechSettings.MAX_RATE) { "rate out of range" }
        require(volume in SpeechSettings.MIN_VOLUME..SpeechSettings.MAX_VOLUME) { "volume out of range" }
    }

    override fun toString(): String = "VoiceSample(voice=$voice, rate=$rate, volume=$volume, text=***)"
}

/**
 * Why [VoicePreview.play] said nothing: [UNAVAILABLE] the program cannot start; [FAILED] it ran and failed, timed
 * out, the voice is not installed, or [VoiceSample.text] holds `[[` or `]]` (refused without starting).
 */
public enum class VoicePreviewError { UNAVAILABLE, FAILED }

/**
 * Plays a voice sample in the settings dialog (FR-N4). Safe on any dispatcher; suspends until the sample ends;
 * cancelling the caller stops it. Never falls back to the system voice. Errors carry no process output.
 */
public interface VoicePreview {
    public suspend fun play(sample: VoiceSample): Outcome<Unit, VoicePreviewError>
}
