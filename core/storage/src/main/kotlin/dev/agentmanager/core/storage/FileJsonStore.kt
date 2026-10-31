package dev.agentmanager.core.storage

import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.catching
import dev.agentmanager.core.domain.flatMap
import dev.agentmanager.core.domain.map
import dev.agentmanager.core.domain.warn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

/**
 * File layout: see [Envelope]. Writes go to `<name>.tmp`, are flushed to disk and
 * then moved over the target with [ATOMIC_MOVE], so a crash leaves either the old or the new file.
 */
internal class FileJsonStore<T>(
    private val file: Path,
    private val spec: StoreSpec<T>,
    private val io: CoroutineDispatcher,
    private val logger: Logger,
) : JsonStore<T> {
    private val mutex = Mutex()

    override suspend fun load(): Outcome<Loaded<T>, StorageError> = locked { read() }

    override suspend fun save(value: T): Outcome<Unit, StorageError> = locked { write(value) }

    override suspend fun update(change: (T) -> T): Outcome<T, StorageError> =
        locked {
            read().flatMap { loaded ->
                val changed = change(loaded.value)
                write(changed).map { changed }
            }
        }

    private suspend fun <R> locked(block: () -> Outcome<R, StorageError>): Outcome<R, StorageError> =
        mutex.withLock { withContext(io) { block() } }

    private fun read(): Outcome<Loaded<T>, StorageError> =
        if (Files.exists(file)) {
            io { Files.readAllBytes(file) }.flatMap { resolve(Envelope.decode(it, spec)) }
        } else {
            Outcome.Ok(Loaded(spec.default))
        }

    private fun resolve(decoded: Decoded<T>): Outcome<Loaded<T>, StorageError> =
        when (decoded) {
            is Decoded.Current -> Outcome.Ok(Loaded(decoded.value))
            is Decoded.Migrated -> write(decoded.value).map { Loaded(decoded.value) }
            is Decoded.Newer -> Outcome.Err(StorageError.NewerSchema(decoded.version, spec.schemaVersion))
            is Decoded.Broken -> moveAside(decoded.reason)
        }

    private fun moveAside(reason: String): Outcome<Loaded<T>, StorageError> {
        val broken = file.resolveSibling("${file.fileName}$BROKEN_SUFFIX")
        return io { Files.move(file, broken, REPLACE_EXISTING) }.map {
            logger.warn("broken config file moved aside", "file" to file.fileName, "reason" to reason)
            Loaded(spec.default, BrokenFile(broken, reason))
        }
    }

    private fun write(value: T): Outcome<Unit, StorageError> {
        val tmp = file.resolveSibling("${file.fileName}$TMP_SUFFIX")
        val written =
            io {
                val content = Envelope.encode(value, spec)
                Files.createDirectories(file.parent)
                FileChannel.open(tmp, CREATE, WRITE, TRUNCATE_EXISTING).use { channel ->
                    channel.write(ByteBuffer.wrap(content))
                    channel.force(true)
                }
                Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING)
            }.map { }
        // A failed write must not leave a stale temp file behind; the target stays as it was.
        if (written is Outcome.Err) io { Files.deleteIfExists(tmp) }
        return written
    }

    private fun <R> io(block: () -> R): Outcome<R, StorageError> =
        catching({ StorageError.IoFailed(file, it.message ?: it.javaClass.simpleName) }, block)

    private companion object {
        const val TMP_SUFFIX = ".tmp"
        const val BROKEN_SUFFIX = ".broken"
    }
}
