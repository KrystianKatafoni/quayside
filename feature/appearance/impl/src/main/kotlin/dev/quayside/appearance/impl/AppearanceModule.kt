package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.ThemeFiles
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.warn
import dev.quayside.core.process.ProcessRunner
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.storage.StoreSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.nio.file.Path

/** The built-in catalog (nine base and 20 shaped themes, 22 typographies); the same content on every call. */
public fun builtInCatalog(): ThemeCatalog = ThemeCatalog(BuiltInSchemes.all, BuiltInTypographies.all)

/**
 * Loads theme.json from [storage] and, with day/night on, reads the macOS mode once before returning. Polls
 * `defaults` through [process] (work dir [home]) every 5 s in [scope] while day/night is on. Never throws.
 */
public suspend fun appearanceModule(
    storage: JsonStorage,
    process: ProcessRunner,
    home: Path,
    scope: CoroutineScope,
    logger: Logger,
): AppearanceApi {
    val catalog = builtInCatalog()
    val serializer =
        ThemeSettingsSerializer(catalog) { keys ->
            logger.warn("theme.json has invalid fields, using defaults", "keys" to keys.sorted())
        }
    val store = storage.store(StoreSpec(JsonThemeStore.FILE_NAME, serializer, AppearanceSettings()))
    // The watcher follows a copy of the saved settings: it is created before the store is open (open needs its mode).
    val followed = MutableStateFlow(AppearanceSettings())
    val watcher = SystemModeWatcher(DefaultsSystemModeAdapter(process, home), followed, scope, logger)
    val api = JsonThemeStore.open(store, catalog, watcher.mode, scope, logger)
    followed.value = api.settings.value
    watcher.start()
    api.refresh()
    scope.launch { api.settings.collect { followed.value = it } }
    return api
}

/**
 * Reads and writes the theme.json files the user picks, checking ids against the catalog of [appearance] at the time
 * of each read (FR-A6).
 */
public fun themeFilesModule(
    appearance: AppearanceApi,
    io: CoroutineDispatcher,
    logger: Logger,
): ThemeFiles = FileThemeFiles({ appearance.catalog.value }, io, logger)
