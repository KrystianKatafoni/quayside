package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.UiScale
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.domain.Outcome
import dev.quayside.terminal.api.TerminalSettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/** What the user did in the dialog. */
public sealed interface AppearanceIntent {
    public data class SelectScheme(
        val id: SchemeId,
    ) : AppearanceIntent

    public data class SelectTypography(
        val id: TypographyId,
    ) : AppearanceIntent

    public data object UseSuggestedTypography : AppearanceIntent

    public data class SelectScale(
        val scale: UiScale,
    ) : AppearanceIntent

    public data class SelectDensity(
        val density: ListDensity,
    ) : AppearanceIntent

    /** null = the font of the chosen typography. */
    public data class SelectTerminalFont(
        val family: String?,
    ) : AppearanceIntent

    /** ±1, clamped to TerminalSettingsRules.FONT_SIZES, no wrap. */
    public data class StepTerminalSize(
        val delta: Int,
    ) : AppearanceIntent

    public data object Apply : AppearanceIntent
}

/** Draft, busy flag and the last error (fixed category); immutable. */
public data class AppearanceDialogState(
    val draft: AppearanceDraft,
    val busy: Boolean,
    val error: ApplyError?,
)

/** State of the Appearance dialog (MVI); runs in [scope] (window scope, Main). Applies at most one change at a time. */
public class AppearanceController(
    private val appearance: AppearanceApi,
    private val terminal: TerminalSettingsRepo,
    private val scope: CoroutineScope,
) {
    private val dialog = MutableStateFlow<AppearanceDialogState?>(null)
    private val generation = AtomicInteger()
    private val writing = Mutex()

    /** The catalog the dialog lists (the one of the appearance module). */
    internal val catalog: ThemeCatalog get() = appearance.catalog

    /** The open dialog; null = closed. */
    public val state: StateFlow<AppearanceDialogState?> = dialog

    /** Opens the dialog with a draft of the saved appearance and terminal settings. */
    public fun open() {
        generation.incrementAndGet()
        dialog.value = AppearanceDialogState(draftOf(appearance.settings.value, terminal.settings.value), false, null)
    }

    /** Closes the dialog and drops the draft; the result of a running apply is dropped too. */
    public fun close() {
        generation.incrementAndGet()
        dialog.value = null
    }

    /** Changes the draft, or applies it ([AppearanceIntent.Apply]); edits are ignored while a save runs. */
    public fun onIntent(intent: AppearanceIntent) {
        val current = dialog.value ?: return
        if (current.busy) return
        val edited = edit(current.draft, intent)
        if (edited == null) apply(current.draft) else dialog.value = current.copy(draft = edited, error = null)
    }

    /** The draft after [intent], or null for [AppearanceIntent.Apply], which is no edit. */
    private fun edit(
        draft: AppearanceDraft,
        intent: AppearanceIntent,
    ): AppearanceDraft? {
        val settings = draft.settings
        return when (intent) {
            AppearanceIntent.Apply -> {
                null
            }

            is AppearanceIntent.SelectScheme -> {
                draft.copy(settings = settings.copy(scheme = intent.id))
            }

            is AppearanceIntent.SelectTypography -> {
                draft.copy(settings = settings.copy(typography = intent.id))
            }

            AppearanceIntent.UseSuggestedTypography -> {
                useSuggestion(draft)
            }

            is AppearanceIntent.SelectScale -> {
                draft.copy(settings = settings.copy(uiScale = intent.scale))
            }

            is AppearanceIntent.SelectDensity -> {
                draft.copy(settings = settings.copy(density = intent.density))
            }

            is AppearanceIntent.SelectTerminalFont -> {
                draft.copy(terminalFont = intent.family)
            }

            is AppearanceIntent.StepTerminalSize -> {
                draft.copy(terminalSize = stepSize(draft.terminalSize, intent.delta))
            }
        }
    }

    private fun useSuggestion(draft: AppearanceDraft): AppearanceDraft =
        suggestedTypography(draft, appearance.catalog)
            ?.let { draft.copy(settings = draft.settings.copy(typography = it)) }
            ?: draft

    private fun apply(draft: AppearanceDraft) {
        val mine = generation.get()
        dialog.update { it?.copy(busy = true, error = null) }
        scope.launch { finish(mine, write(draft)) }
    }

    private suspend fun write(draft: AppearanceDraft): ApplyError? =
        writing.withLock {
            when (appearance.update { draft.settings }) {
                is Outcome.Err -> ApplyError.APPEARANCE
                is Outcome.Ok -> saveTerminal(draft)
            }
        }

    private suspend fun saveTerminal(draft: AppearanceDraft): ApplyError? {
        val saved =
            terminal.update { it.copy(fontFamily = draft.terminalFont, fontSize = draft.terminalSize) }
        return if (saved is Outcome.Ok) null else ApplyError.TERMINAL
    }

    private fun finish(
        mine: Int,
        failure: ApplyError?,
    ) {
        // A result of a dialog that was closed (and maybe reopened) in the meantime is dropped.
        if (generation.get() != mine) return
        dialog.update { if (failure == null) null else it?.copy(busy = false, error = failure) }
    }
}
