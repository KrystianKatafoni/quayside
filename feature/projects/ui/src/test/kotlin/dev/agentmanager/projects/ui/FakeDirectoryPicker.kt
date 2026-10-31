package dev.agentmanager.projects.ui

import kotlinx.coroutines.channels.Channel
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * [DirectoryPicker] that answers with the scripted folders in order. Without a scripted answer [pick]
 * suspends, like an open folder chooser, until [willPick] or [willCancel] is called.
 */
internal class FakeDirectoryPicker : DirectoryPicker {
    private val answers = Channel<Path?>(Channel.UNLIMITED)
    private val count = AtomicInteger()

    /** How many times the chooser was opened. */
    val picks: Int get() = count.get()

    /** The next [pick] returns [dir]. */
    fun willPick(dir: Path) {
        answers.trySend(dir)
    }

    /** The next [pick] returns null, as if the user pressed Cancel. */
    fun willCancel() {
        answers.trySend(null)
    }

    override suspend fun pick(): Path? {
        count.incrementAndGet()
        return answers.receive()
    }
}
