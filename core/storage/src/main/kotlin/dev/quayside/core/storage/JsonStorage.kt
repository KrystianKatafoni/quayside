package dev.quayside.core.storage

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.NoOpLogger
import kotlinx.coroutines.CoroutineDispatcher
import java.nio.file.Path

/**
 * Creates [JsonStore]s for files in [directory] (in production `~/Library/Application Support/Quayside`).
 * The directory is created on the first save.
 *
 * @param io dispatcher for blocking file IO, usually `AppDispatchers.io`.
 * @param logger receives file names and failure reasons, never file contents.
 */
public class JsonStorage(
    private val directory: Path,
    private val io: CoroutineDispatcher,
    private val logger: Logger = NoOpLogger,
) {
    /** A store for [spec]. Create one store per file and share it; two stores on one file are not serialized. */
    public fun <T> store(spec: StoreSpec<T>): JsonStore<T> =
        FileJsonStore(directory.resolve(spec.fileName), spec, io, logger)
}
