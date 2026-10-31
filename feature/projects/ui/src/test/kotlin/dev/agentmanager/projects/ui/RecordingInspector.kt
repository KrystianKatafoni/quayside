package dev.agentmanager.projects.ui

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.projects.api.DirectoryReport
import dev.agentmanager.projects.api.ProjectError
import dev.agentmanager.projects.api.ProjectInspector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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

    /** Folders passed to [inspect], in order. */
    val inspected: List<Path> get() = recorded.value

    /** Lets every waiting and future inspection finish. */
    fun release() {
        gate.complete(Unit)
    }

    override suspend fun inspect(dir: Path): Outcome<DirectoryReport, ProjectError> {
        recorded.update { it.plusElement(dir) } // `+` would add the path segments: Path is Iterable
        gate.await()
        return delegate.inspect(dir)
    }
}
