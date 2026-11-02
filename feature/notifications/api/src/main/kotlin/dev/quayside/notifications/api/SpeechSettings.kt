package dev.quayside.notifications.api

/** A macOS voice by the name `say -v` accepts, e.g. `Zosia`. Not checked against the installed voices. */
@JvmInline
public value class VoiceId(
    public val name: String,
) {
    init {
        require(name.isNotBlank()) { "blank voice" }
    }
}

/**
 * How spoken messages sound and when they are silenced (FR-N4, FR-N5, docs/07 defaults).
 * A kind missing from [templates] uses [defaultTemplate], so "restore default" (AM-704) is a removal.
 *
 * @property voice the macOS voice; null means the system voice. An uninstalled voice falls back to it.
 * @property rate words per minute, [MIN_RATE]..[MAX_RATE].
 * @property volume percent, 0..100.
 * @property templates message per kind with `{sesja}`, `{projekt}`, `{pytanie}`, `{narzędzie}`, `{podsumowanie}`.
 * @property readQuestion after a [NotificationKind.NEEDS_INPUT] message, also read the question when the
 *   template does not contain `{pytanie}`.
 * @property onlyInBackground silent while a window of the app is active.
 * @property muteWhenMicrophoneActive silent while any app records from the microphone.
 */
public data class SpeechSettings(
    val voice: VoiceId? = DEFAULT_VOICE,
    val rate: Int = DEFAULT_RATE,
    val volume: Int = DEFAULT_VOLUME,
    val templates: Map<NotificationKind, String> = emptyMap(),
    val readQuestion: Boolean = true,
    val onlyInBackground: Boolean = true,
    val muteWhenMicrophoneActive: Boolean = true,
) {
    init {
        require(rate in MIN_RATE..MAX_RATE) { "rate out of range" }
        require(volume in 0..MAX_VOLUME) { "volume out of range" }
    }

    /** The template of [kind]: the entry in [templates] or [defaultTemplate]. */
    public fun templateFor(kind: NotificationKind): String = templates[kind] ?: defaultTemplate(kind)

    public companion object {
        public val DEFAULT_VOICE: VoiceId = VoiceId("Zosia")
        public const val DEFAULT_RATE: Int = 190
        public const val MIN_RATE: Int = 90
        public const val MAX_RATE: Int = 360
        public const val DEFAULT_VOLUME: Int = 70
        private const val MAX_VOLUME = 100

        /** The default message of [kind] (docs/07). */
        public fun defaultTemplate(kind: NotificationKind): String =
            when (kind) {
                NotificationKind.NEEDS_INPUT -> "Agent {sesja} w projekcie {projekt} czeka na odpowiedź"
                NotificationKind.PERMISSION -> "{sesja} prosi o zgodę na {narzędzie}"
                NotificationKind.FAILED -> "Uwaga, {sesja} zatrzymał się z błędem"
                NotificationKind.TURN_ENDED -> "{sesja} skończył turę"
                NotificationKind.PULL_REQUEST -> "{sesja} ma pull request do przeglądu"
                NotificationKind.RUNNING_LONG -> "{sesja} pracuje już długo"
                NotificationKind.TERMINAL_BELL -> "Dzwonek w terminalu"
            }
    }
}
