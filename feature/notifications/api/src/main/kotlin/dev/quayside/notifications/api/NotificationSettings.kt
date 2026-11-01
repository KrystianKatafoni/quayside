package dev.quayside.notifications.api

/** What a notification is about (FR-N2). */
public enum class NotificationKind {
    /** The agent waits for a reply. */
    NEEDS_INPUT,

    /** The agent asks for permission to use a tool. */
    PERMISSION,

    /** The agent finished its turn. */
    TURN_ENDED,

    /** The session ended with an error. */
    FAILED,

    /** A pull request is ready for review. */
    PULL_REQUEST,

    /** The session has been working for longer than [NotificationSettings.runningLongMinutes]. */
    RUNNING_LONG,

    /** A terminal rang its bell (FR-T7, AM-509). */
    TERMINAL_BELL,
}

/** How a notification reaches the user (FR-N1). */
public enum class NotificationChannel {
    POPUP,
    VOICE,
    SOUND,
    DOCK_BADGE,
}

/**
 * The notification settings the user configures (FR-N1, docs/07): which [NotificationChannel]s each
 * [NotificationKind] uses, two global switches and the threshold of [NotificationKind.RUNNING_LONG].
 *
 * A kind missing from [matrix] uses its default, so a kind added later needs no migration.
 *
 * @property popups global switch: off silences every popup, whatever the matrix says.
 * @property voice global switch: off silences every spoken message, whatever the matrix says.
 * @property matrix channels chosen per kind; see [channelsFor].
 * @property runningLongMinutes how long a session works before [NotificationKind.RUNNING_LONG] (N in
 *   FR-N2), within [MIN_RUNNING_LONG_MINUTES]..[MAX_RUNNING_LONG_MINUTES].
 * @property popup how popups look and behave (FR-N3).
 */
public data class NotificationSettings(
    val popups: Boolean = true,
    val voice: Boolean = true,
    val matrix: Map<NotificationKind, Set<NotificationChannel>> = emptyMap(),
    val runningLongMinutes: Int = DEFAULT_RUNNING_LONG_MINUTES,
    val popup: PopupSettings = PopupSettings(),
) {
    init {
        require(runningLongMinutes in MIN_RUNNING_LONG_MINUTES..MAX_RUNNING_LONG_MINUTES) {
            "runningLongMinutes out of range"
        }
    }

    /** The channels chosen for [kind]: the entry in [matrix] or, without one, the default of docs/07. */
    public fun channelsFor(kind: NotificationKind): Set<NotificationChannel> = matrix[kind] ?: defaultChannels(kind)

    /** A copy where [channel] is on or off for [kind]. */
    public fun withChannel(
        kind: NotificationKind,
        channel: NotificationChannel,
        enabled: Boolean,
    ): NotificationSettings {
        val current = channelsFor(kind)
        return copy(matrix = matrix + (kind to if (enabled) current + channel else current - channel))
    }

    public companion object {
        /** Default N of [NotificationKind.RUNNING_LONG]. */
        public const val DEFAULT_RUNNING_LONG_MINUTES: Int = 30
        public const val MIN_RUNNING_LONG_MINUTES: Int = 1
        public const val MAX_RUNNING_LONG_MINUTES: Int = 24 * 60

        private val ALL = NotificationChannel.entries.toSet()

        /** The default matrix of docs/07. */
        public fun defaultChannels(kind: NotificationKind): Set<NotificationChannel> =
            when (kind) {
                NotificationKind.NEEDS_INPUT, NotificationKind.PERMISSION, NotificationKind.FAILED -> ALL
                NotificationKind.TURN_ENDED, NotificationKind.TERMINAL_BELL -> setOf(NotificationChannel.POPUP)
                NotificationKind.PULL_REQUEST -> setOf(NotificationChannel.POPUP, NotificationChannel.DOCK_BADGE)
                NotificationKind.RUNNING_LONG -> setOf(NotificationChannel.VOICE)
            }
    }
}
