package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.ThemeFiles
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
import java.nio.file.Path
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

    /** Turns "Dzień i noc z macOS" on or off in the draft. */
    public data object ToggleDayNight : AppearanceIntent

    /** Which of the day and night themes the list edits. */
    public data class SelectSlot(
        val slot: ThemeSlot,
    ) : AppearanceIntent

    /** Asks for a theme file and puts its settings into the draft; saves nothing. */
    public data object Import : AppearanceIntent

    /** Asks for a theme file and adds its custom schemes to the catalog at once (they are saved, not drafted). */
    public data object ImportSchemes : AppearanceIntent

    /** Asks where to save and writes the draft (not the saved settings) as a theme file. */
    public data object Export : AppearanceIntent

    public data object Apply : AppearanceIntent
}

/** What a finished import or export tells the user. */
public enum class FileNotice { IMPORTED, EXPORTED, SCHEMES_IMPORTED }

/** Where the file dialogs of the system are shown; null = the user cancelled. */
public interface ThemeFilePicker {
    /** Asks for a theme file to read. */
    public suspend fun open(): Path?

    /** Asks where to save a theme file; [suggestedName] is the file name offered. */
    public suspend fun save(suggestedName: String): Path?
}

/** Draft, busy flag, the last error (fixed category) and the last import or export notice; immutable. */
public data class AppearanceDialogState(
    val draft: AppearanceDraft,
    val busy: Boolean,
    val error: ApplyError?,
    val notice: FileNotice? = null,
)

/** State of the Appearance dialog (MVI); runs in [scope] (window scope, Main). Applies at most one change at a time. */
public class AppearanceController(
    private val appearance: AppearanceApi,
    private val terminal: TerminalSettingsRepo,
    files: ThemeFiles,
    picker: ThemeFilePicker,
    private val scope: CoroutineScope,
) {
    private val dialog = MutableStateFlow<AppearanceDialogState?>(null)
    private val generation = AtomicInteger()
    private val writing = Mutex()
    private val actions = ThemeFileActions(files, picker, appearance)

    /** The catalog the dialog lists (the one of the appearance module); grows when custom schemes are imported. */
    internal val catalog: StateFlow<ThemeCatalog> get() = appearance.catalog

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

    /** Changes the draft, applies it, imports or exports; all are ignored while a save or a file action runs. */
    public fun onIntent(intent: AppearanceIntent) {
        val current = dialog.value ?: return
        if (current.busy) return
        val edited = editedDraft(current.draft, intent, appearance.catalog.value)
        if (edited == null) {
            start(intent, current.draft)
        } else {
            dialog.value = current.copy(draft = edited, error = null, notice = null)
        }
    }

    /** Starts the action of [intent] when it is one (apply, import, export); other intents are edits. */
    private fun start(
        intent: AppearanceIntent,
        draft: AppearanceDraft,
    ): Unit =
        when (intent) {
            AppearanceIntent.Apply -> apply(draft)
            AppearanceIntent.Import -> runFileAction { actions.import() }
            AppearanceIntent.ImportSchemes -> runFileAction { actions.importSchemes() }
            AppearanceIntent.Export -> runFileAction { actions.export(draft.settings) }
            else -> Unit
        }

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

    private fun runFileAction(action: suspend () -> FileResult) {
        val mine = generation.get()
        dialog.update { it?.copy(busy = true, error = null, notice = null) }
        scope.launch { settle(mine, action()) }
    }

    private fun settle(
        mine: Int,
        result: FileResult,
    ) {
        if (generation.get() != mine) return
        dialog.update { it?.let { shown -> shown.copy(busy = false).withResult(result) } }
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
