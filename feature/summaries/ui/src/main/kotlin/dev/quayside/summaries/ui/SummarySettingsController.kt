package dev.quayside.summaries.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.summaries.api.SummarySettingsError
import dev.quayside.summaries.api.SummarySettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The open dialog. [useModel]: the draft of the switch. [restartNeeded]: the draft differs from what the running
 * app started with, so saving it takes effect at the next start. [saveError]: why the last write changed nothing.
 */
public data class SummaryDialogState(
    val useModel: Boolean,
    val restartNeeded: Boolean,
    val saveError: SummarySettingsError? = null,
)

/**
 * Logic of the "Ustawienia podsumowań" dialog (FR-S7). Main thread; [save] writes in [scope], one write at a time
 * (`saving` is set by [save], cleared when the write ends or the dialog is opened or cancelled), a result arriving
 * after cancel is dropped. Logs nothing (the values are the user's).
 */
public class SummarySettingsController(
    private val repo: SummarySettingsRepo,
    private val scope: CoroutineScope,
) {
    private val dialog = MutableStateFlow<SummaryDialogState?>(null)

    /** The open dialog; null when closed. */
    public val state: StateFlow<SummaryDialogState?> = dialog

    /** True while the dialog is open; hides the other settings commands. */
    public val isOpen: StateFlow<Boolean> =
        dialog.map { it != null }.stateIn(scope, SharingStarted.Eagerly, initialValue = false)

    private var generation = 0
    private var saving = false

    /** Opens the dialog with a draft of the saved switch. */
    public fun open() {
        generation++
        saving = false
        dialog.value = stateOf(repo.useModel.value)
    }

    /** Changes the draft; clears the save error. */
    public fun setUseModel(on: Boolean) {
        if (dialog.value != null) dialog.value = stateOf(on)
    }

    /** Saves the draft; closes on success, else shows the error. */
    public fun save() {
        val draft = dialog.value?.useModel?.takeUnless { saving } ?: return
        saving = true
        val opened = generation
        scope.launch {
            val result = repo.setUseModel(draft)
            if (opened == generation) finish(result)
        }
    }

    /** Closes the dialog and drops the draft. */
    public fun cancel() {
        generation++
        saving = false
        dialog.value = null
    }

    private fun stateOf(on: Boolean) = SummaryDialogState(on, restartNeeded = on != repo.startedWithModel)

    private fun finish(result: Outcome<Unit, SummarySettingsError>) {
        saving = false
        when (result) {
            is Outcome.Ok -> dialog.value = null
            is Outcome.Err -> dialog.update { it?.copy(saveError = result.error) }
        }
    }
}
