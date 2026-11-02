package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.NotificationSettingsError
import dev.quayside.notifications.api.NotificationSettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The open dialog. [invalid]: the field the last save refused. [saveError]: why the last write changed nothing.
 * Part B1 adds the popup to the draft; part B2 adds the list of voices.
 */
public data class NotificationDialogState(
    val draft: NotificationDraft,
    val invalid: InvalidField?,
    val saveError: NotificationSettingsError?,
)

/**
 * Logic of the "Ustawienia powiadomień" dialog (FR-N1, FR-N2). Main thread; [save] writes in [scope], one write at
 * a time (`saving` is set by [save], cleared when the write ends or the dialog is opened or cancelled), a result
 * arriving after cancel is dropped. An edit made while a write runs is lost when the write ends Ok, because the dialog
 * closes. Logs nothing (the values are the user's).
 */
public class NotificationSettingsController(
    private val repo: NotificationSettingsRepo,
    private val scope: CoroutineScope,
) {
    private val dialog = MutableStateFlow<NotificationDialogState?>(null)

    /** The open dialog; null when closed. */
    public val state: StateFlow<NotificationDialogState?> = dialog

    private var generation = 0
    private var saving = false

    /** Opens the dialog with a draft of the saved settings. */
    public fun open() {
        generation++
        saving = false
        dialog.value = NotificationDialogState(draftOf(repo.settings.value), null, null)
    }

    /** Changes the draft; clears the invalid field and the save error. */
    public fun edit(change: (NotificationDraft) -> NotificationDraft) {
        dialog.update { it?.copy(draft = change(it.draft), invalid = null, saveError = null) }
    }

    /** Saves the draft over the current settings; closes on success, else shows the invalid field or the error. */
    public fun save() {
        val draft = dialog.value?.draft?.takeUnless { saving } ?: return
        // Refusal depends only on the draft, so this check is the binding one; the update below cannot refuse.
        val refused = settingsOf(draft, repo.settings.value)
        if (refused is Outcome.Err) {
            dialog.update { it?.copy(invalid = refused.error) }
            return
        }
        saving = true
        val opened = generation
        scope.launch {
            // `?: current` is unreachable: the draft was accepted above.
            val result = repo.update { current -> (settingsOf(draft, current) as? Outcome.Ok)?.value ?: current }
            if (opened == generation) finish(result)
        }
    }

    /** Closes the dialog and drops the draft. */
    public fun cancel() {
        generation++
        saving = false
        dialog.value = null
    }

    private fun finish(result: Outcome<Unit, NotificationSettingsError>) {
        saving = false
        when (result) {
            is Outcome.Ok -> dialog.value = null
            is Outcome.Err -> dialog.update { it?.copy(saveError = result.error) }
        }
    }
}
