package dev.quayside.notifications.api

/** How a project's sessions notify (FR-N7). */
public enum class ProjectProfile {
    /** The global settings apply unchanged. */
    DEFAULT,

    /** Only [NotificationKind.FAILED] notifies, on its channels from the matrix; other kinds say nothing. */
    ERRORS_ONLY,

    /** Only [NotificationChannel.DOCK_BADGE] stays (when the matrix has it for the kind). */
    MUTED,
}

/**
 * The exception of one project (FR-N7). [voice] null means the global voice. The default value is no exception.
 *
 * @property profile which kinds and channels the project's sessions use.
 * @property voice the voice for the spoken messages of this project's sessions; null = [SpeechSettings.voice].
 */
public data class ProjectNotifications(
    val profile: ProjectProfile = ProjectProfile.DEFAULT,
    val voice: VoiceId? = null,
)
