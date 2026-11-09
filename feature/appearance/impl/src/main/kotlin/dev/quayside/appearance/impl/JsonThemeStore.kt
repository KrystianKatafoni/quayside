package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.AppearanceError
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.CustomScheme
import dev.quayside.appearance.api.SchemeError
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.ThemeSpec
import dev.quayside.appearance.api.appliedFor
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import dev.quayside.core.storage.JsonStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the file gave at start: the [settings] and whether the file may be written (false = newer or unreadable). */
internal data class StartState(
    val settings: AppearanceSettings,
    val writable: Boolean,
)

/**
 * [AppearanceApi] persisted in `theme.json` through [JsonStore] (ADR 0023). When the file could not be read or is
 * newer, the defaults are used and nothing is ever written over the file.
 */
internal class JsonThemeStore private constructor(
    private val base: ThemeCatalog,
    private val store: JsonStore<AppearanceSettings>,
    private val systemDark: StateFlow<Boolean?>,
    private val logger: Logger,
    start: StartState,
) : AppearanceApi {
    private val writable = start.writable
    private val saved = MutableStateFlow(start.settings)
    private val inUse = MutableStateFlow(start.settings.appliedFor(systemDark.value))
    private val mutex = Mutex()
    private val themes = MutableStateFlow(catalogWith(base, start.settings.customSchemes))

    override val catalog: StateFlow<ThemeCatalog> = themes.asStateFlow()

    override val settings: StateFlow<AppearanceSettings> = saved.asStateFlow()

    override val applied: StateFlow<AppearanceSettings> = inUse.asStateFlow()

    override suspend fun update(
        change: (AppearanceSettings) -> AppearanceSettings,
    ): Outcome<AppearanceSettings, AppearanceError> =
        mutex.withLock {
            val next = change(saved.value).copy(customSchemes = saved.value.customSchemes)
            val refusal = refusalOf(next)
            when {
                refusal != null -> Outcome.Err(refusal)
                !writable -> Outcome.Err(AppearanceError.StorageFailed)
                else -> persist(next)
            }
        }

    override suspend fun addScheme(scheme: CustomScheme): Outcome<ThemeSpec, SchemeError> =
        mutex.withLock {
            val audited = auditedThemeOf(scheme)
            when {
                themes.value.theme(scheme.id) != null -> Outcome.Err(SchemeError.Taken(scheme.id))
                audited is Outcome.Err -> audited
                !writable -> Outcome.Err(SchemeError.StorageFailed)
                else -> persistScheme(scheme)
            }
        }

    /** Re-reads the mode now; [applied] is otherwise recomputed in the scope given to [open]. */
    fun refresh() {
        inUse.value = saved.value.appliedFor(systemDark.value)
    }

    // Not cancellable: a cancellation after the atomic move must not leave the file and the state apart.
    private suspend fun persist(next: AppearanceSettings): Outcome<AppearanceSettings, AppearanceError> =
        if (save(next)) Outcome.Ok(next) else Outcome.Err(AppearanceError.StorageFailed)

    private suspend fun persistScheme(scheme: CustomScheme): Outcome<ThemeSpec, SchemeError> {
        val next = saved.value.copy(customSchemes = saved.value.customSchemes + scheme)
        return if (save(next)) Outcome.Ok(themeOf(scheme)) else Outcome.Err(SchemeError.StorageFailed)
    }

    /** Saves [next] and publishes it with its catalog; false (and nothing published) when the file was not written. */
    private suspend fun save(next: AppearanceSettings): Boolean =
        withContext(NonCancellable) {
            when (store.save(next)) {
                is Outcome.Ok -> {
                    themes.value = catalogWith(base, next.customSchemes)
                    saved.value = next
                    refresh()
                    true
                }

                is Outcome.Err -> {
                    logger.warn("theme.json save failed")
                    false
                }
            }
        }

    private fun refusalOf(candidate: AppearanceSettings): AppearanceError? {
        val catalog = themes.value
        val unknownScheme =
            listOf(candidate.scheme, candidate.dayNight.day, candidate.dayNight.night)
                .firstOrNull { catalog.theme(it) == null }
        return when {
            unknownScheme != null -> AppearanceError.UnknownScheme(unknownScheme)
            catalog.typography(candidate.typography) == null -> AppearanceError.UnknownTypography(candidate.typography)
            else -> null
        }
    }

    internal companion object {
        const val FILE_NAME = "theme.json"

        /**
         * Loads [store] once; [catalog] is the built-in one, the custom schemes of the file join it. A saved id
         * outside gives the default of that field and a category in [logger]. `applied` is
         * `settings.appliedFor(systemDark)` and is collected in [scope].
         */
        suspend fun open(
            store: JsonStore<AppearanceSettings>,
            catalog: ThemeCatalog,
            systemDark: StateFlow<Boolean?>,
            scope: CoroutineScope,
            logger: Logger,
        ): JsonThemeStore {
            val api = JsonThemeStore(catalog, store, systemDark, logger, loadStart(store, catalog, logger))
            scope.launch {
                combine(api.saved, systemDark) { s, dark -> s.appliedFor(dark) }.collect {
                    api.inUse.value =
                        it
                }
            }
            return api
        }
    }
}
