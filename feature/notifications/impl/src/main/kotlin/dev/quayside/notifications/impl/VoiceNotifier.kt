package dev.quayside.notifications.impl

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import dev.quayside.notifications.api.NotificationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Speaks the deliveries of the voice channel: templates, at most one message per 20 s, the rest grouped (FR-N4).
 * Checks again before a grouped message that the voice is still on and allowed. One consumer in [scope] speaks the
 * messages one after another; the 20 s timer lives there too, so cancelling [scope] ends everything. Assumes
 * [SpeechPort.speak] reports failures as [Outcome.Err] and does not throw (the adapter maps them); a throw would end
 * the consumer.
 */
internal class VoiceNotifier(
    private val speech: SpeechPort,
    private val settings: StateFlow<NotificationSettings>,
    private val scope: CoroutineScope,
    private val context: () -> DeliveryContext,
    private val logger: Logger,
) {
    private val lock = Any()
    private var grouping = VoiceGrouping()
    private val queue = Channel<String>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (text in queue) speak(text)
        }
    }

    /** A delivery on the voice channel for [project] (its display name, null when unknown). Never suspends. */
    fun offer(
        delivery: Delivery,
        project: String?,
    ) {
        val config = settings.value.speech
        val text =
            VoiceTemplates.render(
                config.templateFor(delivery.kind),
                delivery.kind,
                delivery.session,
                project,
                config.readQuestion,
            ) ?: return
        synchronized(lock) {
            val step = grouping.offer(VoiceMessage(delivery.session?.id, delivery.kind, text))
            apply(step)
        }
    }

    /** Must run under [lock]. */
    private fun apply(step: VoiceStep) {
        grouping = step.state
        val action = step.action
        if (action is VoiceAction.Speak) {
            queue.trySend(action.text)
            scope.launch {
                delay(COOLDOWN)
                cooldownEnded()
            }
        }
    }

    private fun cooldownEnded() {
        synchronized(lock) {
            val step = grouping.cooldownEnded()
            if (step.action is VoiceAction.Speak && !allowedNow()) grouping = VoiceGrouping() else apply(step)
        }
    }

    private fun allowedNow(): Boolean = settings.value.voice && voiceAllowed(settings.value.speech, context())

    private suspend fun speak(text: String) {
        val config = settings.value.speech
        val result = speech.speak(Utterance(text, config.voice, config.rate, config.volume))
        if (result is Outcome.Err) logger.warn("voice failed", "reason" to result.error.category())
    }

    private fun SpeechError.category(): String =
        when (this) {
            SpeechError.Unavailable -> "unavailable"
            is SpeechError.Failed -> "failed"
            SpeechError.TimedOut -> "timed-out"
        }

    private companion object {
        val COOLDOWN = 20.seconds
    }
}
