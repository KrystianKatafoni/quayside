package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationKind

/**
 * Pure reducer: at most one sound per cooldown (2 s, counted from the hand-over of a sound to the player). During the
 * cooldown one sound waits; a more important kind replaces it (FAILED > PERMISSION > NEEDS_INPUT > PULL_REQUEST >
 * TURN_ENDED > RUNNING_LONG > TERMINAL_BELL), a less or equally important one is dropped.
 */
internal data class SoundQueue(
    val cooling: Boolean = false,
    val pending: NotificationKind? = null,
) {
    /** A sound of [kind] was delivered. */
    fun offer(kind: NotificationKind): SoundStep =
        when {
            !cooling -> SoundStep(SoundQueue(cooling = true), kind)
            pending == null || importance(kind) > importance(pending) -> SoundStep(copy(pending = kind), null)
            else -> SoundStep(this, null)
        }

    /** The cooldown of 2 s ended. */
    fun cooldownEnded(): SoundStep =
        if (pending == null) SoundStep(SoundQueue(), null) else SoundStep(SoundQueue(cooling = true), pending)

    private fun importance(kind: NotificationKind): Int = IMPORTANCE.indexOf(kind)

    private companion object {
        /** From the least to the most important. */
        val IMPORTANCE =
            listOf(
                NotificationKind.TERMINAL_BELL,
                NotificationKind.RUNNING_LONG,
                NotificationKind.TURN_ENDED,
                NotificationKind.PULL_REQUEST,
                NotificationKind.NEEDS_INPUT,
                NotificationKind.PERMISSION,
                NotificationKind.FAILED,
            )
    }
}

/** The next [state] and the kind to play now, if any. */
internal data class SoundStep(
    val state: SoundQueue,
    val play: NotificationKind?,
)
