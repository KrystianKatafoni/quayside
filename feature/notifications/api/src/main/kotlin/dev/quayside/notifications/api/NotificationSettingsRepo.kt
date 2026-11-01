package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * The stored [NotificationSettings]. Threading: safe to call from any dispatcher; changes are applied
 * one after another, so concurrent [update] calls never lose a change.
 */
public interface NotificationSettingsRepo {
    /** The current settings; the defaults when nothing is stored or the stored file could not be used. */
    public val settings: StateFlow<NotificationSettings>

    /**
     * Applies [change] to the current settings, saves the result and then publishes it in [settings].
     * Nothing is published when saving fails.
     */
    public suspend fun update(
        change: (NotificationSettings) -> NotificationSettings,
    ): Outcome<Unit, NotificationSettingsError>
}

/** Why [NotificationSettingsRepo.update] changed nothing. */
public enum class NotificationSettingsError {
    /** The file could not be written (permissions, full disk). */
    WRITE_FAILED,

    /** The file was written by a newer version of the app; it is never overwritten. */
    NEWER_SCHEMA,
}
