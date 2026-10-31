package dev.agentmanager.projects.impl

import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.storage.JsonStorage
import dev.agentmanager.core.storage.StoreSpec
import dev.agentmanager.projects.api.ProjectsApi
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Opens the project list stored as `projects.json` in [storage] and returns it as [ProjectsApi].
 * Suspends until the file is loaded, so [ProjectsApi.storageIssue] and [ProjectsApi.projects] are final
 * on return; never throws except cancellation. The caller picks the directory (`JsonStorage(appSupportDir, …)`).
 *
 * @param io dispatcher for path resolution (`toRealPath()`), usually `AppDispatchers.io`.
 * @param logger gets project ids and fixed categories only, never paths or display names (ENG-C8).
 */
public suspend fun projectsModule(
    storage: JsonStorage,
    io: CoroutineDispatcher,
    logger: Logger,
): ProjectsApi =
    JsonProjectStore.open(
        store = storage.store(StoreSpec(JsonProjectStore.FILE_NAME, ProjectListSerializer, emptyList())),
        io = io,
        logger = logger,
    )
