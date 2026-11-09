package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceApi
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.CustomScheme
import dev.quayside.appearance.api.SchemeError
import dev.quayside.appearance.api.SchemeId
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

    /** The scheme of the editor was added; [all] are the custom schemes saved now. */
    data class SchemeAdded(
        val all: List<CustomScheme>,
    ) : FileResult

    /** The custom scheme [id] was removed; [all] are the ones left and [saved] the settings in effect. */
    data class SchemeRemoved(
        val id: SchemeId,
        val all: List<CustomScheme>,
        val saved: AppearanceSettings,
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

        is FileResult.SchemeAdded -> {
            copy(
                draft = draft.copy(settings = draft.settings.copy(customSchemes = result.all)),
                editor = null,
                notice = FileNotice.SCHEME_ADDED,
            )
        }

        is FileResult.SchemeRemoved -> {
            copy(draft = draft.withoutScheme(result), notice = FileNotice.SCHEME_REMOVED)
        }

        FileResult.Exported -> {
            copy(notice = FileNotice.EXPORTED)
        }

        is FileResult.Failed -> {
            copy(error = result.error)
        }
    }

/** [this] without the removed scheme: the theme it chose is replaced by the saved one (which cannot be removed). */
private fun AppearanceDraft.withoutScheme(removed: FileResult.SchemeRemoved): AppearanceDraft {
    fun pick(
        chosen: SchemeId,
        saved: SchemeId,
    ): SchemeId = if (chosen == removed.id) saved else chosen
    val now = settings
    val dayNight =
        now.dayNight.copy(
            day = pick(now.dayNight.day, removed.saved.dayNight.day),
            night = pick(now.dayNight.night, removed.saved.dayNight.night),
        )
    val next =
        now.copy(
            scheme = pick(now.scheme, removed.saved.scheme),
            dayNight = dayNight,
            customSchemes = removed.all,
        )
    return copy(settings = next)
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

    /** The scheme action [intent] asks for (add the editor's scheme, remove or export one), or null when it is none. */
    fun schemeAction(
        intent: AppearanceIntent,
        editor: SchemeEditor?,
    ): (suspend () -> FileResult)? =
        when (intent) {
            AppearanceIntent.SaveScheme -> editor?.toScheme()?.let { scheme -> suspend { addScheme(scheme) } }
            is AppearanceIntent.RemoveScheme -> suspend { removeScheme(intent.id) }
            is AppearanceIntent.ExportScheme -> suspend { exportScheme(intent.id) }
            else -> null
        }

    private suspend fun addScheme(scheme: CustomScheme): FileResult =
        when (val added = appearance.addScheme(scheme)) {
            is Outcome.Ok -> FileResult.SchemeAdded(appearance.settings.value.customSchemes)
            is Outcome.Err -> FileResult.Failed(schemeErrorOf(added.error))
        }

    private suspend fun removeScheme(id: SchemeId): FileResult =
        when (val removed = appearance.removeScheme(id)) {
            is Outcome.Ok -> {
                FileResult.SchemeRemoved(
                    id,
                    appearance.settings.value.customSchemes,
                    appearance.settings.value,
                )
            }

            is Outcome.Err -> {
                FileResult.Failed(schemeErrorOf(removed.error))
            }
        }

    /** Writes the custom scheme [id] alone (the other settings are the defaults) where the user picks. */
    private suspend fun exportScheme(id: SchemeId): FileResult {
        val scheme =
            appearance.settings.value.customSchemes
                .firstOrNull { it.id == id }
        val target = if (scheme == null) null else picker.save("${id.value}.json")
        return when {
            scheme == null -> FileResult.Failed(ApplyError.SCHEME_NOT_FOUND)
            target == null -> FileResult.Cancelled
            files.write(AppearanceSettings(customSchemes = listOf(scheme)), target) is Outcome.Ok -> FileResult.Exported
            else -> FileResult.Failed(ApplyError.EXPORT)
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

private fun schemeErrorOf(error: SchemeError): ApplyError =
    when (error) {
        is SchemeError.Taken -> ApplyError.SCHEME_TAKEN
        is SchemeError.LowContrast -> ApplyError.SCHEME_CONTRAST
        SchemeError.StorageFailed -> ApplyError.SCHEME_SAVE
        is SchemeError.InUse -> ApplyError.SCHEME_IN_USE
        is SchemeError.NotFound -> ApplyError.SCHEME_NOT_FOUND
    }
