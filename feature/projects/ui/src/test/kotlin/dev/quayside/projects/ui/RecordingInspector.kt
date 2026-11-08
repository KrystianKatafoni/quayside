package dev.quayside.projects.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.projects.api.DirectoryReport
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.ProjectInspector
import dev.quayside.projects.api.SubfolderName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * [ProjectInspector] that records the inspected folders and answers like [delegate]. With [gated] every
 * inspection suspends until [release], so a test can look at the "Checking…" state or cancel it.
 */
internal class RecordingInspector(
    private val delegate: ProjectInspector,
    gated: Boolean = false,
) : ProjectInspector {
    private val gate = CompletableDeferred<Unit>().apply { if (!gated) complete(Unit) }
    private val recorded = MutableStateFlow(emptyList<Path>())
    private val listed = MutableStateFlow(emptyList<Path>())
    private val heldListing = MutableStateFlow<CompletableDeferred<Unit>?>(null)

    /** Folders passed to [inspect], in order. */
    val inspected: List<Path> get() = recorded.value

    /** Folders passed to [subfolders], in order. */
    val listings: List<Path> get() = listed.value

    /**
     * The next [subfolders] call waits until the returned deferred is completed. The wait is not cancellable, so the
     * answer (read before the wait) can arrive after the caller was cancelled, the way a blocking read finishes late.
     */
    fun holdListing(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { heldListing.value = it }

    /** Lets every waiting and future inspection finish. */
    fun release() {
        gate.complete(Unit)
    }

    override suspend fun inspect(dir: Path): Outcome<DirectoryReport, ProjectError> {
        recorded.update { it.plusElement(dir) } // `+` would add the path segments: Path is Iterable
        gate.await()
        return delegate.inspect(dir)
    }

    override suspend fun subfolders(dir: Path): Outcome<List<SubfolderName>, ProjectError> {
        listed.update { it.plusElement(dir) }
        gate.await()
        val answer = delegate.subfolders(dir)
        heldListing.getAndUpdate { null }?.let { held -> withContext(NonCancellable) { held.await() } }
        return answer
    }
}
