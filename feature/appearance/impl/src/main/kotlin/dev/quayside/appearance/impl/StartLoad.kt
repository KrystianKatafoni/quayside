package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import dev.quayside.core.storage.JsonStore
import dev.quayside.core.storage.StorageError

/**
 * Loads [store] for the start of the app. A broken file was moved aside by the store (the defaults apply and the file
 * may be written); a newer or unreadable file gives the defaults and is never written. Logs fixed categories only.
 */
internal suspend fun loadStart(
    store: JsonStore<AppearanceSettings>,
    catalog: ThemeCatalog,
    logger: Logger,
): StartState =
    when (val loaded = store.load()) {
        is Outcome.Ok -> {
            if (loaded.value.recovered != null) logger.warn("theme.json recovered from a broken file")
            StartState(knownIdsOnly(loaded.value.value, catalog, logger), writable = true)
        }

        is Outcome.Err -> {
            val newer = loaded.error is StorageError.NewerSchema
            logger.warn(if (newer) "theme.json is newer" else "theme.json unreadable")
            StartState(AppearanceSettings(), writable = false)
        }
    }

/**
 * [settings] with every id outside [catalog] replaced by the default of its field; the fallback is logged. A defence
 * for stores that skip the serializer (in-memory ones): from the file, [ThemeSettingsSerializer] has already fixed the
 * ids and reports the keys.
 */
private fun knownIdsOnly(
    settings: AppearanceSettings,
    catalog: ThemeCatalog,
    logger: Logger,
): AppearanceSettings {
    val defaults = AppearanceSettings()
    val fixed =
        settings.copy(
            scheme = settings.scheme.takeIf { catalog.theme(it) != null } ?: defaults.scheme,
            typography = settings.typography.takeIf { catalog.typography(it) != null } ?: defaults.typography,
            dayNight =
                settings.dayNight.copy(
                    day = settings.dayNight.day.takeIf { catalog.theme(it) != null } ?: defaults.dayNight.day,
                    night = settings.dayNight.night.takeIf { catalog.theme(it) != null } ?: defaults.dayNight.night,
                ),
        )
    if (fixed != settings) logger.warn("theme.json names an id outside the catalog, using the default")
    return fixed
}
