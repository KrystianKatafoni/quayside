package dev.quayside.appearance.ui

import dev.quayside.appearance.api.ThemeCatalog

/** The draft after [intent], or null when [intent] is no edit (apply, import, export). */
internal fun editedDraft(
    draft: AppearanceDraft,
    intent: AppearanceIntent,
    catalog: ThemeCatalog,
): AppearanceDraft? {
    val settings = draft.settings
    return when (intent) {
        is AppearanceIntent.SelectScheme -> selectInSlot(draft, intent.id)
        is AppearanceIntent.SelectTypography -> draft.copy(settings = settings.copy(typography = intent.id))
        AppearanceIntent.UseSuggestedTypography -> useSuggestion(draft, catalog)
        is AppearanceIntent.SelectScale -> draft.copy(settings = settings.copy(uiScale = intent.scale))
        is AppearanceIntent.SelectDensity -> draft.copy(settings = settings.copy(density = intent.density))
        is AppearanceIntent.SelectTerminalFont -> draft.copy(terminalFont = intent.family)
        is AppearanceIntent.StepTerminalSize -> draft.copy(terminalSize = stepSize(draft.terminalSize, intent.delta))
        else -> editedDayNight(draft, intent)
    }
}

private fun editedDayNight(
    draft: AppearanceDraft,
    intent: AppearanceIntent,
): AppearanceDraft? =
    when (intent) {
        AppearanceIntent.ToggleDayNight -> {
            val dayNight = draft.settings.dayNight
            draft.copy(settings = draft.settings.copy(dayNight = dayNight.copy(enabled = !dayNight.enabled)))
        }

        is AppearanceIntent.SelectSlot -> {
            draft.copy(slot = intent.slot)
        }

        else -> {
            null
        }
    }

private fun useSuggestion(
    draft: AppearanceDraft,
    catalog: ThemeCatalog,
): AppearanceDraft =
    suggestedTypography(draft, catalog)
        ?.let { draft.copy(settings = draft.settings.copy(typography = it)) }
        ?: draft
