package dev.agentmanager.projects.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.flatMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory [ProjectsApi] for tests of other modules. Passes [ProjectsApiContract].
 *
 * Validates input with [ProjectInputRules], like the real implementation, then resolves the
 * normalized path with [realPath] (default: unchanged; null = nothing there → [ProjectError.NotFound])
 * and asks [pathKind] what is at the real path (default [PathKind.DIRECTORY]), so `/Users/test/...`
 * works without disk. Contract tests pass [PathKind.onDisk] and [PathKind.realPathOnDisk].
 * Added projects get ids `project0001`, `project0002`, … in order.
 *
 * [storageIssue] is reported as [ProjectsApi.storageIssue]. With [StorageIssue.READ_FAILED] or
 * [StorageIssue.NEWER_SCHEMA] every command returns [ProjectError.StorageFailed] with the matching
 * [StorageProblem] and changes nothing, as the real store does.
 */
public class FakeProjectsApi(
    initial: List<Project> = emptyList(),
    private val pathKind: (Path) -> PathKind = { PathKind.DIRECTORY },
    private val realPath: (Path) -> Path? = { it },
    storageIssue: StorageIssue? = null,
) : ProjectsApi {
    private val blockedBy: StorageProblem? =
        when (storageIssue) {
            StorageIssue.READ_FAILED -> StorageProblem.READ_FAILED
            StorageIssue.NEWER_SCHEMA -> StorageProblem.NEWER_SCHEMA
            StorageIssue.RECOVERED_FROM_BROKEN_FILE, null -> null
        }
    private val state = MutableStateFlow(initial)
    private val recorded = MutableStateFlow(emptyList<String>())
    private val pendingFailure = MutableStateFlow<ProjectError?>(null)
    private val lastId = AtomicInteger()

    /**
     * Commands called so far, in order, as `<command> <argument>`: `add <dir as given>`,
     * `remove <id>`, `rename <id>`, e.g. `add /Users/test/projects/agent-manager`, `remove project0001`.
     */
    public val calls: List<String> get() = recorded.value

    /** Makes the next command return `Err(error)` without changing anything; later commands work again. */
    public fun failNext(error: ProjectError) {
        pendingFailure.value = error
    }

    override val projects: StateFlow<List<Project>> = state.asStateFlow()

    override val storageIssue: StateFlow<StorageIssue?> = MutableStateFlow(storageIssue).asStateFlow()

    override suspend fun add(
        dir: Path,
        displayName: String?,
    ): Outcome<Project, ProjectError> {
        val failure = begin("add $dir")
        if (failure != null) return Outcome.Err(failure)
        return ProjectInputRules.normalizeDir(dir).flatMap { normalized ->
            existingDirectory(normalized).flatMap { real ->
                ProjectInputRules.displayName(displayName, real).flatMap { name -> insert(real, name) }
            }
        }
    }

    override suspend fun remove(id: ProjectId): Outcome<Unit, ProjectError> {
        val failure = begin("remove ${id.value}")
        if (failure != null) return Outcome.Err(failure)
        var result: Outcome<Unit, ProjectError> = Outcome.Err(ProjectError.UnknownProject(id))
        state.update { current ->
            val kept = current.filterNot { it.id == id }
            result = if (kept.size < current.size) Outcome.Ok(Unit) else Outcome.Err(ProjectError.UnknownProject(id))
            kept
        }
        return result
    }

    override suspend fun rename(
        id: ProjectId,
        displayName: String,
    ): Outcome<Unit, ProjectError> {
        val failure = begin("rename ${id.value}")
        if (failure != null) return Outcome.Err(failure)
        val project = state.value.firstOrNull { it.id == id } ?: return Outcome.Err(ProjectError.UnknownProject(id))
        return ProjectInputRules.displayName(displayName, project.dir).flatMap { name -> replaceName(id, name) }
    }

    /** Records [call] and takes the pending failure, if any, else the storage failure, if any. */
    private fun begin(call: String): ProjectError? {
        recorded.update { it + call }
        return pendingFailure.getAndUpdate { null } ?: blockedBy?.let(ProjectError::StorageFailed)
    }

    /** Real path of the normalized [dir] when it is a directory; errors name [dir] as given. */
    private fun existingDirectory(dir: Path): Outcome<Path, ProjectError> {
        val real = realPath(dir) ?: return Outcome.Err(ProjectError.NotFound(dir))
        return when (pathKind(real)) {
            PathKind.DIRECTORY -> Outcome.Ok(real)
            PathKind.FILE -> Outcome.Err(ProjectError.NotADirectory(dir))
            PathKind.MISSING -> Outcome.Err(ProjectError.NotFound(dir))
        }
    }

    // Compare-and-set loop: the duplicate check and the insert are one atomic step. A retry may
    // skip an id number, never reuse one.
    private fun insert(
        dir: Path,
        name: String,
    ): Outcome<Project, ProjectError> {
        while (true) {
            val current = state.value
            val existing = current.firstOrNull { it.dir == dir }
            if (existing != null) return Outcome.Err(ProjectError.AlreadyAdded(existing.id))
            val project = Project(ProjectId(ID_FORMAT.format(lastId.incrementAndGet())), dir, name)
            if (state.compareAndSet(current, current + project)) return Outcome.Ok(project)
        }
    }

    private fun replaceName(
        id: ProjectId,
        name: String,
    ): Outcome<Unit, ProjectError> {
        var result: Outcome<Unit, ProjectError> = Outcome.Err(ProjectError.UnknownProject(id))
        state.update { current ->
            result = if (current.any { it.id == id }) Outcome.Ok(Unit) else Outcome.Err(ProjectError.UnknownProject(id))
            current.map { if (it.id == id) it.copy(displayName = name) else it }
        }
        return result
    }

    private companion object {
        const val ID_FORMAT = "project%04d"
    }
}
