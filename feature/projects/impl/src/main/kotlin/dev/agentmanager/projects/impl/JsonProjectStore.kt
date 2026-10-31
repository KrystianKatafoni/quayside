package dev.agentmanager.projects.impl

import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.flatMap
import dev.agentmanager.core.domain.info
import dev.agentmanager.core.domain.map
import dev.agentmanager.core.domain.warn
import dev.agentmanager.core.storage.JsonStore
import dev.agentmanager.projects.api.Project
import dev.agentmanager.projects.api.ProjectError
import dev.agentmanager.projects.api.ProjectInputRules
import dev.agentmanager.projects.api.ProjectsApi
import dev.agentmanager.projects.api.StorageIssue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.UUID

/**
 * [ProjectsApi] persisted in `projects.json` through [JsonStore] (ADR 0009).
 *
 * The list in memory is the source of truth: it is loaded once by [open], and every command checks and
 * changes it under [mutex], saves the whole new list and only then publishes it. Path resolution runs
 * on [io] outside the lock. Logs carry project ids and fixed categories only (ENG-C8).
 */
internal class JsonProjectStore private constructor(
    private val store: JsonStore<List<Project>>,
    private val io: CoroutineDispatcher,
    private val logger: Logger,
    private val newId: () -> ProjectId,
    startup: StartupState,
) : ProjectsApi {
    private val state = MutableStateFlow(startup.projects)
    private val blockedBy = startup.blockedBy?.let(ProjectError::StorageFailed)
    private val mutex = Mutex()

    override val projects: StateFlow<List<Project>> = state.asStateFlow()

    override val storageIssue: StateFlow<StorageIssue?> = MutableStateFlow(startup.issue).asStateFlow()

    override suspend fun add(
        dir: Path,
        displayName: String?,
    ): Outcome<Project, ProjectError> {
        if (blockedBy != null) return Outcome.Err(blockedBy)
        return ProjectInputRules
            .normalizeDir(dir)
            .flatMap { normalized -> RealPaths.resolve(normalized, io) }
            .flatMap { real -> ProjectInputRules.displayName(displayName, real).flatMap { name -> insert(real, name) } }
    }

    override suspend fun remove(id: ProjectId): Outcome<Unit, ProjectError> {
        if (blockedBy != null) return Outcome.Err(blockedBy)
        return mutex.withLock {
            val current = state.value
            if (current.none { it.id == id }) return@withLock Outcome.Err(ProjectError.UnknownProject(id))
            commit(current.filterNot { it.id == id }).also { logged(it, "project removed", id) }
        }
    }

    override suspend fun rename(
        id: ProjectId,
        displayName: String,
    ): Outcome<Unit, ProjectError> {
        if (blockedBy != null) return Outcome.Err(blockedBy)
        return mutex.withLock {
            val current = state.value
            val project =
                current.firstOrNull { it.id == id } ?: return@withLock Outcome.Err(ProjectError.UnknownProject(id))
            ProjectInputRules.displayName(displayName, project.dir).flatMap { name ->
                commit(current.map { if (it.id == id) it.copy(displayName = name) else it })
                    .also { logged(it, "project renamed", id) }
            }
        }
    }

    /** The duplicate check, the new id and the save are one step under [mutex]. */
    private suspend fun insert(
        dir: Path,
        name: String,
    ): Outcome<Project, ProjectError> =
        mutex.withLock {
            val current = state.value
            val existing = current.firstOrNull { it.dir == dir }
            if (existing != null) return@withLock Outcome.Err(ProjectError.AlreadyAdded(existing.id))
            val project = Project(freshId(current), dir, name)
            commit(current + project).map { project }.also { logged(it, "project added", project.id) }
        }

    /**
     * Draws again while [newId] returns an id already on the list. A source that keeps colliding is a
     * programming error (random UUIDs never do), so it fails fast instead of spinning under [mutex].
     */
    private fun freshId(current: List<Project>): ProjectId =
        checkNotNull(
            generateSequence { newId() }.take(MAX_ID_DRAWS).firstOrNull { id -> current.none { it.id == id } },
        ) { "newId kept returning ids already in use" }

    /**
     * Saves [list] and then publishes it. Not cancellable: a cancellation after the atomic move must not
     * leave the file and [projects] in different states.
     */
    private suspend fun commit(list: List<Project>): Outcome<Unit, ProjectError> =
        withContext(NonCancellable) {
            when (val saved = store.save(list)) {
                is Outcome.Ok -> {
                    state.value = list
                    Outcome.Ok(Unit)
                }

                is Outcome.Err -> {
                    val problem = saved.error.toWriteProblem()
                    logger.warn("projects file save failed", "problem" to problem.name)
                    Outcome.Err(ProjectError.StorageFailed(problem))
                }
            }
        }

    private fun logged(
        outcome: Outcome<*, ProjectError>,
        message: String,
        id: ProjectId,
    ) {
        if (outcome is Outcome.Ok) logger.info(message, PROJECT_ID to id.value)
    }

    internal companion object {
        const val FILE_NAME = "projects.json"
        private const val PROJECT_ID = "projectId"
        private const val MAX_ID_DRAWS = 16

        /** Loads once and maps the result with [StartupState.of]. */
        suspend fun open(
            store: JsonStore<List<Project>>,
            io: CoroutineDispatcher,
            logger: Logger,
            newId: () -> ProjectId = { ProjectId(UUID.randomUUID().toString()) },
        ): JsonProjectStore {
            val startup = StartupState.of(store.load())
            startup.issue?.let { logger.warn(startupMessage(it), "issue" to it.name) }
            return JsonProjectStore(store, io, logger, newId, startup)
        }

        private fun startupMessage(issue: StorageIssue): String =
            when (issue) {
                StorageIssue.RECOVERED_FROM_BROKEN_FILE -> "projects file recovered from a broken copy"
                StorageIssue.READ_FAILED -> "projects file unreadable"
                StorageIssue.NEWER_SCHEMA -> "projects file newer schema"
            }
    }
}
