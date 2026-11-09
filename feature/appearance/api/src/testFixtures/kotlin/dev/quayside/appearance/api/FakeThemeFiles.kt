package dev.quayside.appearance.api

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path

/**
 * In-memory [ThemeFiles] for tests of other modules: files are kept per path, nothing touches the disk. A test plants
 * files with [put], [putNewer] and [putInvalidValue], or makes every call fail with [readFailure] / [writeFailure].
 * Passes [ThemeFilesContract].
 */
public class FakeThemeFiles : ThemeFiles {
    private val mutex = Mutex()
    private val files = mutableMapOf<Path, Outcome<AppearanceSettings, ThemeFileError>>()
    private val schemeFiles = mutableMapOf<Path, List<CustomScheme>>()
    private val readPaths = mutableListOf<Path>()
    private val writes = linkedMapOf<Path, AppearanceSettings>()

    /** When set, every [read] returns this error (after being recorded in [reads]). */
    @Volatile
    public var readFailure: ThemeFileError? = null

    /** When set, every [write] returns this error and writes nothing. */
    @Volatile
    public var writeFailure: ThemeFileError? = null

    /** The paths [read] was called with, in call order. */
    public val reads: List<Path> get() = synchronized(readPaths) { readPaths.toList() }

    /** What [write] stored, by path; a later write to the same path replaces the earlier one. */
    public val written: Map<Path, AppearanceSettings> get() = synchronized(writes) { LinkedHashMap(writes) }

    /** Plants a readable theme file with [settings] at [path]; [read] returns it without `customSchemes`. */
    public fun put(
        path: Path,
        settings: AppearanceSettings,
    ) {
        synchronized(files) { files[path] = Outcome.Ok(settings.copy(customSchemes = emptyList())) }
    }

    /** Plants a theme file at [path] whose `customSchemes` are [schemes] (it has the default settings otherwise). */
    public fun putSchemes(
        path: Path,
        schemes: List<CustomScheme>,
    ) {
        synchronized(files) {
            files[path] = Outcome.Ok(AppearanceSettings())
            schemeFiles[path] = schemes
        }
    }

    /** Plants a file written by a newer app version: reading it returns [ThemeFileError.NewerVersion]. */
    public fun putNewer(
        path: Path,
        found: Int,
    ) {
        synchronized(files) { files[path] = Outcome.Err(ThemeFileError.NewerVersion(found)) }
    }

    /** Plants a file with a present but invalid value (e.g. an unknown theme id): reading it is InvalidValue. */
    public fun putInvalidValue(path: Path) {
        synchronized(files) { files[path] = Outcome.Err(ThemeFileError.InvalidValue) }
    }

    override suspend fun read(source: Path): Outcome<AppearanceSettings, ThemeFileError> =
        mutex.withLock {
            synchronized(readPaths) { readPaths.add(source) }
            val failure = readFailure
            when {
                failure != null -> Outcome.Err(failure)
                else -> synchronized(files) { files[source] } ?: Outcome.Err(ThemeFileError.ReadFailed)
            }
        }

    override suspend fun readSchemes(source: Path): Outcome<List<CustomScheme>, ThemeFileError> =
        mutex.withLock {
            synchronized(readPaths) { readPaths.add(source) }
            val failure = readFailure
            when {
                failure != null -> {
                    Outcome.Err(failure)
                }

                else -> {
                    synchronized(files) {
                        when (val file = files[source]) {
                            null -> Outcome.Err(ThemeFileError.ReadFailed)
                            is Outcome.Err -> Outcome.Err(file.error)
                            is Outcome.Ok -> Outcome.Ok(schemeFiles[source].orEmpty())
                        }
                    }
                }
            }
        }

    override suspend fun write(
        settings: AppearanceSettings,
        target: Path,
    ): Outcome<Unit, ThemeFileError> =
        mutex.withLock {
            val failure = writeFailure
            if (failure != null) {
                Outcome.Err(failure)
            } else {
                synchronized(writes) { writes[target] = settings }
                synchronized(files) {
                    files[target] = Outcome.Ok(settings.copy(customSchemes = emptyList()))
                    schemeFiles[target] = settings.customSchemes
                }
                Outcome.Ok(Unit)
            }
        }
}
