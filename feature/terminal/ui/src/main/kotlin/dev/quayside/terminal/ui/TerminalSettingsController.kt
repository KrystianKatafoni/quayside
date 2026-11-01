package dev.quayside.terminal.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.StartDirectory
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsError
import dev.quayside.terminal.api.TerminalSettingsField
import dev.quayside.terminal.api.TerminalSettingsIssue
import dev.quayside.terminal.api.TerminalSettingsRepo
import dev.quayside.terminal.api.TerminalSettingsRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The settings as the dialog edits them (AM-508): numbers stay text until [TerminalSettingsController.save], so a
 * half-typed value is not lost. A blank [fontFamily] means the default font.
 */
public data class SettingsDraft(
    val shell: ShellProfile,
    val startDirectory: StartDirectory,
    val fontFamily: String,
    val fontSize: String,
    val scrollbackLines: String,
    val ligatures: Boolean,
    val optionAsMeta: Boolean,
    val bellNotification: Boolean,
)

/**
 * The open dialog. [invalid]: the field the last save refused. [storageFailed]: the last save could not be written.
 * [notice]: what happened to `terminal.json` at start, null when nothing.
 */
public data class SettingsDialogState(
    val draft: SettingsDraft,
    val invalid: TerminalSettingsField?,
    val storageFailed: Boolean,
    val notice: TerminalSettingsIssue?,
)

/**
 * Logic of the "Ustawienia terminala" dialog (AM-508, FR-T7). Methods run on the Main thread; [save] writes in
 * [scope] (the window scope), one write at a time; a result that arrives after Cancel is dropped.
 * Logs nothing: the values are the user's.
 */
public class TerminalSettingsController(
    private val repo: TerminalSettingsRepo,
    private val scope: CoroutineScope,
) {
    private val dialog = MutableStateFlow<SettingsDialogState?>(null)

    /** The open dialog; null when closed. */
    public val state: StateFlow<SettingsDialogState?> = dialog

    private var generation = 0
    private var saving = false

    /** Opens the dialog with a draft of the saved settings and the start notice of the repo. */
    public fun open() {
        generation++
        saving = false
        dialog.value = SettingsDialogState(draftOf(repo.settings.value), null, false, repo.issue.value)
    }

    /** Changes the draft; clears the invalid field and the storage failure. */
    public fun edit(change: (SettingsDraft) -> SettingsDraft) {
        dialog.update { it?.copy(draft = change(it.draft), invalid = null, storageFailed = false) }
    }

    /** Saves the draft; closes the dialog on success, else shows the invalid field or the storage failure. */
    public fun save() {
        val draft = dialog.value?.draft?.takeUnless { saving } ?: return
        val settings = settingsOf(draft)
        val refused = TerminalSettingsRules.validate(settings)
        if (refused is Outcome.Err) {
            dialog.update { it?.copy(invalid = refused.error.field) }
            return
        }
        saving = true
        val opened = generation
        scope.launch {
            val result = repo.update { settings }
            if (opened == generation) finish(result)
        }
    }

    /** Closes the dialog and drops the draft. */
    public fun cancel() {
        generation++
        saving = false
        dialog.value = null
    }

    private fun finish(result: Outcome<TerminalSettings, TerminalSettingsError>) {
        saving = false
        when (result) {
            is Outcome.Ok -> {
                dialog.value = null
            }

            is Outcome.Err -> {
                dialog.update {
                    when (val error = result.error) {
                        is TerminalSettingsError.Invalid -> it?.copy(invalid = error.field)
                        TerminalSettingsError.StorageFailed -> it?.copy(storageFailed = true)
                    }
                }
            }
        }
    }
}

private fun draftOf(settings: TerminalSettings): SettingsDraft =
    SettingsDraft(
        shell = settings.shell,
        startDirectory = settings.startDirectory,
        fontFamily = settings.fontFamily.orEmpty(),
        fontSize = settings.fontSize.toString(),
        scrollbackLines = settings.scrollbackLines.toString(),
        ligatures = settings.ligatures,
        optionAsMeta = settings.optionAsMeta,
        bellNotification = settings.bellNotification,
    )

/**
 * Text that is not a number becomes [NOT_A_NUMBER], which lies outside every allowed size and scrollback value,
 * so the rules refuse that field.
 */
private fun settingsOf(draft: SettingsDraft): TerminalSettings =
    TerminalSettings(
        shell = draft.shell,
        startDirectory = draft.startDirectory,
        fontFamily = draft.fontFamily,
        fontSize = draft.fontSize.trim().toIntOrNull() ?: NOT_A_NUMBER,
        scrollbackLines = draft.scrollbackLines.trim().toIntOrNull() ?: NOT_A_NUMBER,
        ligatures = draft.ligatures,
        optionAsMeta = draft.optionAsMeta,
        bellNotification = draft.bellNotification,
    )

private const val NOT_A_NUMBER: Int = Int.MIN_VALUE
