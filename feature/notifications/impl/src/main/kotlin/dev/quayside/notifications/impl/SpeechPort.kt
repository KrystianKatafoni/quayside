package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.VoiceId

/**
 * One thing to say. [voice] null is the system voice, [rate] is words per minute, [volume] percent.
 * `toString` hides [text] (ENG-C8).
 */
internal data class Utterance(
    val text: String,
    val voice: VoiceId?,
    val rate: Int,
    val volume: Int,
) {
    override fun toString(): String = "Utterance(voice=$voice, rate=$rate, volume=$volume, text=***)"
}

/** Why an [Utterance] was not spoken. */
internal sealed class SpeechError {
    /** The speech program does not exist. */
    internal data object Unavailable : SpeechError()

    /** The speech program ended with [exitCode] other than 0. */
    internal data class Failed(
        val exitCode: Int,
    ) : SpeechError()

    /** The speech program ran too long and was killed. */
    internal data object TimedOut : SpeechError()
}

/** Port to the speech engine. Speaks to the end; cancelling the caller stops it. */
internal interface SpeechPort {
    suspend fun speak(utterance: Utterance): Outcome<Unit, SpeechError>
}
