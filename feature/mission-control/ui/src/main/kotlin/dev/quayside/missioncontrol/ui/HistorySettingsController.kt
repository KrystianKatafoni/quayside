package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.HistoryLimit
import dev.quayside.sessions.api.HistorySettingsError
import dev.quayside.sessions.api.SessionHistorySettings
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The open "Stare sesje z dysku" dialog: the saved [maxAge] and [maxPerProject], and [saveError] when the last choice
 * could not be saved (the choice shown is then still the saved one).
 */
public data class HistoryDialogState(
    val maxAge: HistoryAge,
    val maxPerProject: HistoryLimit,
    val saveError: HistorySettingsError? = null,
)

/**
 * Logic of the "Stare sesje z dysku" dialog (FR-P9): every choice is saved at once (no "Zapisz" button), the dialog
 * shows what is saved, a result that arrives after the dialog was closed (also after it was opened again) is
 * dropped. Main thread; writes in [scope]. Logs nothing.
 */
public class HistorySettingsController(
    private val repo: SessionHistorySettingsRepo,
    private val scope: CoroutineScope,
) {
    private val dialog = MutableStateFlow<HistoryDialogState?>(null)
    private val opened = MutableStateFlow(false)

    /** Counts the openings and closings; a result of an older one is ignored. */
    private var generation = 0

    /** The open dialog; null when closed. */
    public val state: StateFlow<HistoryDialogState?> = dialog.asStateFlow()

    /** True while the dialog is open; hides the other settings commands. */
    public val isOpen: StateFlow<Boolean> = opened.asStateFlow()

    /** Opens the dialog with the saved choices. */
    public fun open() {
        generation++
        val saved = repo.settings.value
        dialog.value = HistoryDialogState(saved.maxAge, saved.maxPerProject)
        opened.value = true
    }

    /** Saves [age] at once; ignored while the dialog is closed. */
    public fun chooseAge(age: HistoryAge): Unit = save { it.copy(maxAge = age) }

    /** Saves [limit] at once; ignored while the dialog is closed. */
    public fun chooseLimit(limit: HistoryLimit): Unit = save { it.copy(maxPerProject = limit) }

    /** Closes the dialog; a save in progress is dropped. */
    public fun close() {
        generation++
        dialog.value = null
        opened.value = false
    }

    private fun save(change: (SessionHistorySettings) -> SessionHistorySettings) {
        if (dialog.value == null) return
        val mine = generation
        scope.launch {
            val result = repo.update(change)
            if (mine != generation) return@launch
            dialog.value =
                when (result) {
                    is Outcome.Ok -> HistoryDialogState(result.value.maxAge, result.value.maxPerProject)
                    is Outcome.Err -> dialog.value?.copy(saveError = result.error)
                }
        }
    }
}
