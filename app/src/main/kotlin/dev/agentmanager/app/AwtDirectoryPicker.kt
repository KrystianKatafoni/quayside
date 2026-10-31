package dev.agentmanager.app

import dev.agentmanager.projects.ui.DirectoryPicker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

/**
 * [DirectoryPicker] on `java.awt.FileDialog`. With `apple.awt.fileDialogForDirectories=true`, set in `main`
 * before the first use of AWT, macOS shows its native folder chooser (`NSOpenPanel`). The modal dialog is shown
 * on [ui], the AWT event thread, which keeps handling events while it is open.
 */
internal class AwtDirectoryPicker(
    private val owner: Frame,
    private val ui: CoroutineDispatcher,
) : DirectoryPicker {
    override suspend fun pick(): Path? =
        withContext(ui) {
            val dialog = FileDialog(owner, Strings["picker.title"], FileDialog.LOAD)
            dialog.isVisible = true // Returns when the user picks a folder or cancels.
            val folder = dialog.file
            val parent = dialog.directory
            dialog.dispose()
            if (folder == null || parent == null) null else Path.of(parent, folder)
        }
}
