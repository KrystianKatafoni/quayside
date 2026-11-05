package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.NotificationKind

/** A short macOS system sound, a file `<fileName>.aiff` in `/System/Library/Sounds`. */
internal enum class SystemSound(
    val fileName: String,
) {
    GLASS("Glass"),
    PING("Ping"),
    BASSO("Basso"),
    HERO("Hero"),
    POP("Pop"),
    SUBMARINE("Submarine"),
    TINK("Tink"),
}

/** The sound of [kind]; fixed mapping (docs/07 has none, see question 2 of AM-812). */
internal fun soundFor(kind: NotificationKind): SystemSound =
    when (kind) {
        NotificationKind.NEEDS_INPUT -> SystemSound.GLASS
        NotificationKind.PERMISSION -> SystemSound.PING
        NotificationKind.FAILED -> SystemSound.BASSO
        NotificationKind.TURN_ENDED -> SystemSound.HERO
        NotificationKind.PULL_REQUEST -> SystemSound.POP
        NotificationKind.RUNNING_LONG -> SystemSound.SUBMARINE
        NotificationKind.TERMINAL_BELL -> SystemSound.TINK
    }

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

/** Port to the sound player. Plays to the end; cancelling the caller stops it. Never throws for player failures. */
internal interface SoundPort {
    suspend fun play(sound: SystemSound): Outcome<Unit, SoundError>
}
