package dev.quayside.core.storage

import dev.quayside.core.domain.Outcome
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path

/**
 * One configuration value of type [T] persisted as a JSON file owned by a single module (docs/11 §8).
 *
 * All operations are serialized: concurrent [update] calls never lose a change. Safe to call from any
 * dispatcher; implementations do file IO on their own IO dispatcher. Errors come back as [StorageError].
 */
public interface JsonStore<T> {
    /**
     * Reads the current value. A missing file yields the default. A file that cannot be parsed is moved
     * aside as `<name>.broken` and the default is returned with [Loaded.recovered] set, so the UI can
     * tell the user. An older schema is migrated and written back.
     */
    public suspend fun load(): Outcome<Loaded<T>, StorageError>

    /** Replaces the stored value atomically: readers see either the old or the new file, never a mix. */
    public suspend fun save(value: T): Outcome<Unit, StorageError>

    /**
     * Loads, applies [change] and saves as one step; returns the saved value. A broken file is recovered
     * as in [load], but the [BrokenFile] is only logged, so owners call [load] first to show it in the UI.
     */
    public suspend fun update(change: (T) -> T): Outcome<T, StorageError>
}

/** Result of [JsonStore.load]: the value and, when the file was broken, where it was moved. */
public data class Loaded<out T>(
    val value: T,
    val recovered: BrokenFile? = null,
)

/** A configuration file that could not be read and was moved to [movedTo]. */
public data class BrokenFile(
    val movedTo: Path,
    val reason: String,
)

/** Why a store operation failed. The file on disk is left unchanged in both cases. */
public sealed interface StorageError {
    /** The file was written by a newer app version; it is never downgraded or overwritten. */
    public data class NewerSchema(
        val found: Int,
        val supported: Int,
    ) : StorageError

    /** Reading or writing [path] failed, e.g. missing permissions or a full disk. Never quotes file contents. */
    public data class IoFailed(
        val path: Path,
        val reason: String,
    ) : StorageError
}

/**
 * Transforms the `data` object of schema version [fromVersion] into version `fromVersion + 1`.
 * A migration that throws (or old data that is not an object) makes the file broken: it is moved aside.
 */
public class Migration(
    public val fromVersion: Int,
    public val migrate: (JsonObject) -> JsonObject,
)

/**
 * Describes one store: file name inside the storage directory, serializer, default value and the current
 * [schemaVersion]. [migrations] must cover every version from 1 up to `schemaVersion - 1`.
 *
 * @throws IllegalArgumentException for a blank or nested [fileName], a version below 1 or missing migrations.
 */
public class StoreSpec<T>(
    public val fileName: String,
    public val serializer: KSerializer<T>,
    public val default: T,
    public val schemaVersion: Int = 1,
    public val migrations: List<Migration> = emptyList(),
) {
    init {
        require(fileName.isNotBlank() && '/' !in fileName) { "fileName must be a plain, non-blank file name" }
        require(schemaVersion >= 1) { "schemaVersion must be at least 1" }
        require(migrations.map { it.fromVersion }.sorted() == (1 until schemaVersion).toList()) {
            "migrations must cover versions 1 until $schemaVersion exactly once"
        }
    }
}
