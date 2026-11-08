package dev.quayside.projects.impl

import dev.quayside.core.domain.Logger
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.storage.StoreSpec
import dev.quayside.projects.api.ProjectsApi
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
        store =
            storage.store(
                StoreSpec(
                    fileName = JsonProjectStore.FILE_NAME,
                    serializer = ProjectListSerializer,
                    default = emptyList(),
                    schemaVersion = ProjectListMigration.SCHEMA_VERSION,
                    migrations = ProjectListMigration.migrations(),
                ),
            ),
        io = io,
        logger = logger,
    )
