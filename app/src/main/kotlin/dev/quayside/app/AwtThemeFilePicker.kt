package dev.quayside.app

import dev.quayside.appearance.ui.ThemeFilePicker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

/** The AWT property that makes `FileDialog` choose folders on macOS; `main` sets it to `true` for projects. */
internal const val DIRECTORIES_PROPERTY = "apple.awt.fileDialogForDirectories"

/**
 * Runs [show] with [DIRECTORIES_PROPERTY] set to `false` (files, not folders) and restores the previous value, also
 * when [show] throws; a property that was not set stays not set. Call it on the AWT event thread only.
 */
internal fun <T> withFileDialogForFiles(show: () -> T): T {
    val before = System.getProperty(DIRECTORIES_PROPERTY)
    System.setProperty(DIRECTORIES_PROPERTY, "false")
    try {
        return show()
    } finally {
        restore(before)
    }
}

private fun restore(before: String?) {
    if (before == null) System.clearProperty(DIRECTORIES_PROPERTY) else System.setProperty(DIRECTORIES_PROPERTY, before)
}

/**
 * [ThemeFilePicker] on `java.awt.FileDialog` shown on [ui], the AWT event thread, inside [withFileDialogForFiles]; a
 * cancelled dialog gives null.
 */
internal class AwtThemeFilePicker(
    private val owner: Frame,
    private val ui: CoroutineDispatcher,
) : ThemeFilePicker {
    override suspend fun open(): Path? = show(FileDialog.LOAD, Strings["themePicker.open.title"], null)

    override suspend fun save(suggestedName: String): Path? =
        show(FileDialog.SAVE, Strings["themePicker.save.title"], suggestedName)

    /** The modal dialog runs on [ui], which keeps handling events while it is open. */
    private suspend fun show(
        mode: Int,
        title: String,
        suggestedName: String?,
    ): Path? =
        withContext(ui) {
            withFileDialogForFiles {
                val dialog = FileDialog(owner, title, mode)
                suggestedName?.let { dialog.file = it }
                dialog.isVisible = true // Returns when the user picks a file or cancels.
                val file = dialog.file
                val parent = dialog.directory
                dialog.dispose()
                if (file == null || parent == null) null else Path.of(parent, file)
            }
        }
}
