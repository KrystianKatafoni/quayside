package dev.agentmanager.core.storage

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [JsonStore] kept in memory, for tests of modules that persist configuration (ENG-I7). */
public class InMemoryJsonStore<T>(
    initial: T,
) : JsonStore<T> {
    private val mutex = Mutex()
    private var current: T = initial

    /** When set, every operation fails with this error, e.g. to test how owners report storage problems. */
    public var failure: StorageError? = null

    /** Returned by the next [load] as [Loaded.recovered], once, to simulate a broken file. */
    public var recoverNextLoad: BrokenFile? = null

    /** Number of successful saves, including the ones done by [update]. */
    public var saves: Int = 0
        private set

    override suspend fun load(): Outcome<Loaded<T>, StorageError> =
        mutex.withLock {
            failure?.let { return@withLock Outcome.Err(it) }
            Outcome.Ok(Loaded(current, recoverNextLoad)).also { recoverNextLoad = null }
        }

    override suspend fun save(value: T): Outcome<Unit, StorageError> = update { value }.map { }

    override suspend fun update(change: (T) -> T): Outcome<T, StorageError> =
        mutex.withLock {
            failure?.let { return@withLock Outcome.Err(it) }
            current = change(current)
            saves++
            Outcome.Ok(current)
        }
}
