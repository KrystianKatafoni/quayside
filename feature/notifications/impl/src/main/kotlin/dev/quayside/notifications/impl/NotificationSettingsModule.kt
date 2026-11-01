package dev.quayside.notifications.impl

import dev.quayside.core.domain.Logger
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.storage.StoreSpec
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.NotificationSettingsRepo

/**
 * Opens the notification settings stored as `notifications.json` in [storage]. Suspends until the file
 * is loaded, so [NotificationSettingsRepo.settings] is final on return; never throws except cancellation.
 * The caller picks the directory (`JsonStorage(appSupportDir, …)`).
 *
 * @param logger gets fixed categories only, never file contents (ENG-C8).
 */
public suspend fun notificationSettingsModule(
    storage: JsonStorage,
    logger: Logger,
): NotificationSettingsRepo =
    JsonNotificationSettingsRepo.open(
        store =
            storage.store(
                StoreSpec(
                    JsonNotificationSettingsRepo.FILE_NAME,
                    NotificationSettingsSerializer,
                    NotificationSettings(),
                ),
            ),
        logger = logger,
    )
