package dev.agentmanager.projects.ui

import java.nio.file.Path

/**
 * Opens the system folder chooser; null = the user cancelled. Callable from any thread: an implementation
 * switches to the UI thread itself if its chooser needs it.
 */
public fun interface DirectoryPicker {
    public suspend fun pick(): Path?
}
