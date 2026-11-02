package dev.quayside.notifications.impl

import dev.quayside.notifications.api.SpeechSettings

/** What is true at the moment of a delivery (docs/11 §4): a window of the app is active, a microphone records. */
internal data class DeliveryContext(
    val appActive: Boolean,
    val microphoneActive: Boolean,
)

/** True when the voice may speak under [speech] in [context] (FR-N5). */
internal fun voiceAllowed(
    speech: SpeechSettings,
    context: DeliveryContext,
): Boolean =
    !(speech.onlyInBackground && context.appActive) &&
        !(speech.muteWhenMicrophoneActive && context.microphoneActive)
