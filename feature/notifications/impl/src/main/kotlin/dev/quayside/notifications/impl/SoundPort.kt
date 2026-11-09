package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.SoundId

/** Why a sound was not played. */
internal sealed class SoundError {
    /** The player program does not exist or could not start. */
    internal data object Unavailable : SoundError()

    /** The player ended with [exitCode] other than 0, also after the fallback sound. */
    internal data class Failed(
        val exitCode: Int,
    ) : SoundError()

    /** The player ran longer than its timeout and was killed. */
    internal data object TimedOut : SoundError()
}

/**
 * Port to the sound player. Plays [sound] at [volume] percent to the end; cancelling the caller stops it. Never throws
 * for player failures.
 */
internal interface SoundPort {
    suspend fun play(
        sound: SoundId,
        volume: Int,
    ): Outcome<Unit, SoundError>
}
