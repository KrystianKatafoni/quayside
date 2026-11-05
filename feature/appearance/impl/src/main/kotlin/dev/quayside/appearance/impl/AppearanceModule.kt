package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.AppearanceError
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private class BuiltInAppearance : AppearanceApi {
    override val catalog: ThemeCatalog = ThemeCatalog(BuiltInSchemes.all, BuiltInTypographies.all)

    private val current = MutableStateFlow(AppearanceSettings())

    override val settings: StateFlow<AppearanceSettings> = current.asStateFlow()

    override suspend fun update(
        change: (AppearanceSettings) -> AppearanceSettings,
    ): Outcome<AppearanceSettings, AppearanceError> {
        while (true) {
            val before = current.value
            val after = change(before)
            val refusal = refusalOf(after)
            if (refusal != null) return Outcome.Err(refusal)
            if (current.compareAndSet(before, after)) return Outcome.Ok(after)
        }
    }

    private fun refusalOf(candidate: AppearanceSettings): AppearanceError? =
        when {
            catalog.theme(candidate.scheme) == null -> AppearanceError.UnknownScheme(candidate.scheme)
            catalog.typography(candidate.typography) == null -> AppearanceError.UnknownTypography(candidate.typography)
            else -> null
        }
}

/** The only public entry point of this module; called from the composition root in `app`. */
public fun appearanceModule(): AppearanceApi = BuiltInAppearance()
