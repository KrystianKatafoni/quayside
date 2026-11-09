package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.CustomScheme
import dev.quayside.appearance.api.SchemeError
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

    /** Custom schemes were added; [all] are the custom schemes saved now. */
    data class SchemesImported(
        val all: List<CustomScheme>,
    ) : FileResult

    data object Exported : FileResult

    data class Failed(
        val error: ApplyError,
    ) : FileResult
}

/** [state] after [result]: an import replaces the settings of the draft, a failure shows its category. */
internal fun AppearanceDialogState.withResult(result: FileResult): AppearanceDialogState =
    when (result) {
        FileResult.Cancelled -> {
            this
        }

        is FileResult.Imported -> {
            copy(
                draft = draft.copy(settings = result.settings.copy(customSchemes = draft.settings.customSchemes)),
                notice = FileNotice.IMPORTED,
            )
        }

        is FileResult.SchemesImported -> {
            copy(
                draft = draft.copy(settings = draft.settings.copy(customSchemes = result.all)),
                notice = FileNotice.SCHEMES_IMPORTED,
            )
        }

        FileResult.Exported -> {
            copy(notice = FileNotice.EXPORTED)
        }

        is FileResult.Failed -> {
            copy(error = result.error)
        }
    }

/**
 * Asks [picker] for a file and runs [files] on it; never applies settings. Custom schemes of a file go to [appearance]
 * at once, one by one in file order: the first refused scheme stops the import, the ones before it stay.
 */
internal class ThemeFileActions(
    private val files: ThemeFiles,
    private val picker: ThemeFilePicker,
    private val appearance: AppearanceApi,
) {
    suspend fun import(): FileResult {
        val source = picker.open() ?: return FileResult.Cancelled
        return when (val read = files.read(source)) {
            is Outcome.Ok -> FileResult.Imported(read.value)
            is Outcome.Err -> FileResult.Failed(importErrorOf(read.error))
        }
    }

    suspend fun importSchemes(): FileResult {
        val source = picker.open() ?: return FileResult.Cancelled
        return when (val read = files.readSchemes(source)) {
            is Outcome.Err -> FileResult.Failed(importErrorOf(read.error))
            is Outcome.Ok -> addAll(read.value)
        }
    }

    private suspend fun addAll(schemes: List<CustomScheme>): FileResult {
        val failure = if (schemes.isEmpty()) ApplyError.SCHEMES_NONE else firstFailure(schemes)
        return if (failure == null) {
            FileResult.SchemesImported(appearance.settings.value.customSchemes)
        } else {
            FileResult.Failed(failure)
        }
    }

    private suspend fun firstFailure(schemes: List<CustomScheme>): ApplyError? {
        for (scheme in schemes) {
            val added = appearance.addScheme(scheme)
            if (added is Outcome.Err) return schemeErrorOf(added.error)
        }
        return null
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

private fun schemeErrorOf(error: SchemeError): ApplyError =
    when (error) {
        is SchemeError.Taken -> ApplyError.SCHEME_TAKEN
        is SchemeError.LowContrast -> ApplyError.SCHEME_CONTRAST
        SchemeError.StorageFailed -> ApplyError.SCHEME_SAVE
    }
