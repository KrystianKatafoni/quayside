package dev.quayside.notifications.impl

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Plays the deliveries of the sound channel through [sound], one after another in one consumer in [scope]; the
 * cooldown timer lives there too (it counts from the hand-over of a sound to the player). Before a sound plays, also
 * one that waited for the cooldown or behind a long play, it checks again that quiet hours (FR-N6) did not start.
 * A failure is logged as `warn("sound failed", "reason" to <category>)`, never the kind or path.
 * Assumes [SoundPort.play] reports failures as [Outcome.Err] and does not throw; a throw would end the consumer.
 */
internal class SoundNotifier(
    private val sound: SoundPort,
    private val settings: StateFlow<NotificationSettings>,
    private val scope: CoroutineScope,
    private val context: () -> DeliveryContext,
    private val logger: Logger,
) {
    private val lock = Any()
    private var queue = SoundQueue()
    private val plays = Channel<NotificationKind>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (kind in plays) play(kind)
        }
    }

    /** A delivery on the sound channel. Never suspends. */
    fun offer(delivery: Delivery) {
        synchronized(lock) { apply(queue.offer(delivery.kind)) }
    }

    /** Must run under [lock]. */
    private fun apply(step: SoundStep) {
        queue = step.state
        val kind = step.play ?: return
        plays.trySend(kind)
        scope.launch {
            delay(COOLDOWN)
            cooldownEnded()
        }
    }

    private fun cooldownEnded() {
        synchronized(lock) {
            val step = queue.cooldownEnded()
            if (step.play != null &&
                quietNow(settings.value.quietHours, context())
            ) {
                queue = SoundQueue()
            } else {
                apply(step)
            }
        }
    }

    private suspend fun play(kind: NotificationKind) {
        // A sound can sit in the channel behind a long play; quiet hours may have started meanwhile.
        if (quietNow(settings.value.quietHours, context())) return
        val chosen = settings.value.sound
        val result = sound.play(chosen.soundFor(kind), chosen.volume)
        if (result is Outcome.Err) logger.warn("sound failed", "reason" to result.error.category())
    }

    private fun SoundError.category(): String =
        when (this) {
            SoundError.Unavailable -> "unavailable"
            is SoundError.Failed -> "failed"
            SoundError.TimedOut -> "timed-out"
        }

    private companion object {
        val COOLDOWN = 2.seconds
    }
}
