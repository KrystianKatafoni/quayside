package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.ThemeFileError
import dev.quayside.appearance.api.ThemeFiles
import dev.quayside.core.domain.Outcome

private const val SUGGESTED_NAME = "quayside-theme.json"

/** How an import or export ended. */
internal sealed interface FileResult {
    /** The user closed the system dialog. */
    data object Cancelled : FileResult

    data class Imported(
        val settings: AppearanceSettings,
    ) : FileResult

    data object Exported : FileResult

    data class Failed(
        val error: ApplyError,
    ) : FileResult
}

/** [state] after [result]: an import replaces the settings of the draft, a failure shows its category. */
internal fun AppearanceDialogState.withResult(result: FileResult): AppearanceDialogState =
    when (result) {
        FileResult.Cancelled -> this
        is FileResult.Imported -> copy(draft = draft.copy(settings = result.settings), notice = FileNotice.IMPORTED)
        FileResult.Exported -> copy(notice = FileNotice.EXPORTED)
        is FileResult.Failed -> copy(error = result.error)
    }

/** Asks [picker] for a file and runs [files] on it; never applies anything. */
internal class ThemeFileActions(
    private val files: ThemeFiles,
    private val picker: ThemeFilePicker,
) {
    suspend fun import(): FileResult {
        val source = picker.open() ?: return FileResult.Cancelled
        return when (val read = files.read(source)) {
            is Outcome.Ok -> FileResult.Imported(read.value)
            is Outcome.Err -> FileResult.Failed(importErrorOf(read.error))
        }
    }

    suspend fun export(settings: AppearanceSettings): FileResult {
        val target = picker.save(SUGGESTED_NAME) ?: return FileResult.Cancelled
        return when (files.write(settings, target)) {
            is Outcome.Ok -> FileResult.Exported
            is Outcome.Err -> FileResult.Failed(ApplyError.EXPORT)
        }
    }
}

private fun importErrorOf(error: ThemeFileError): ApplyError =
    when (error) {
        ThemeFileError.ReadFailed, ThemeFileError.WriteFailed -> ApplyError.IMPORT_UNREADABLE
        ThemeFileError.NotThemeFile -> ApplyError.IMPORT_NOT_THEME
        is ThemeFileError.NewerVersion -> ApplyError.IMPORT_NEWER
        ThemeFileError.InvalidValue -> ApplyError.IMPORT_INVALID
    }
