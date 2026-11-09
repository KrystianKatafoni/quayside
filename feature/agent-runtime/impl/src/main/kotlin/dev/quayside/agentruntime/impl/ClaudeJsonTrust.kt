package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.TrustError
import dev.quayside.agentruntime.api.WorkspaceTrust
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.catching
import dev.quayside.core.domain.debug
import dev.quayside.core.domain.flatMap
import dev.quayside.core.domain.map
import dev.quayside.core.domain.warn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission

/**
 * File-based [WorkspaceTrust] over `<configHome>/.claude.json` (ADR 0031). One call at a time; the file is read,
 * merged ([TrustJson]), written to a temporary file next to it and moved over it atomically, after a second read
 * has shown that nobody changed it meanwhile. Exception messages and file content never reach the log.
 * [tuning] holds the test-only bottlenecks of the IO.
 */
internal class ClaudeJsonTrust(
    private val configHome: Path,
    private val io: CoroutineDispatcher,
    private val logger: Logger,
    private val tuning: TrustTuning = TrustTuning(),
) : WorkspaceTrust {
    private val mutex = Mutex()
    private val file: Path get() = configHome.resolve(CONFIG_FILE)

    override suspend fun trust(dir: Path): Outcome<Unit, TrustError> =
        mutex.withLock { withContext(io) { trustBlocking(dir) } }

    private fun trustBlocking(dir: Path): Outcome<Unit, TrustError> {
        val outcome = realKey(dir).flatMap(::commitWithRetry)
        when (outcome) {
            is Outcome.Ok -> logger.debug("workspace trust", "changed" to outcome.value)
            is Outcome.Err -> logger.warn("workspace trust failed", "reason" to outcome.error.name)
        }
        return outcome.map { }
    }

    private fun realKey(dir: Path): Outcome<String, TrustError> =
        catching({ TrustError.DIRECTORY_MISSING }) { dir.toRealPath() }.flatMap { real ->
            if (Files.isDirectory(real)) Outcome.Ok(real.toString()) else Outcome.Err(TrustError.DIRECTORY_MISSING)
        }

    /** True when the file was written; the attempt repeats on fresh content while the file keeps changing. */
    private fun commitWithRetry(key: String): Outcome<Boolean, TrustError> =
        generateSequence { attempt(key) }
            .take(MAX_ATTEMPTS)
            .firstOrNull { it !is Outcome.Ok || it.value != null }
            ?.flatMap { Outcome.Ok(it == true) }
            ?: Outcome.Err(TrustError.CONCURRENT_CHANGE)

    /** Null = the file changed meanwhile, try again. */
    private fun attempt(key: String): Outcome<Boolean?, TrustError> =
        readConfig().flatMap { original ->
            TrustJson.withTrust(original.text, key).flatMap { merged ->
                if (merged == null) Outcome.Ok(false) else write(original, merged)
            }
        }

    private fun readConfig(): Outcome<Original, TrustError> =
        catching({ TrustError.CONFIG_UNREADABLE }) { classify(file, tuning.maxBytes) }
            .flatMap { problem -> if (problem == null) Outcome.Ok(Unit) else Outcome.Err(problem) }
            .flatMap {
                catching({ TrustError.CONFIG_UNREADABLE }) { readNoFollow(file) to permissionsOf(file) }
            }.flatMap { (bytes, permissions) ->
                catching({ TrustError.CONFIG_INVALID }) { Original(bytes, utf8(bytes), permissions) }
            }

    private fun write(
        original: Original,
        merged: String,
    ): Outcome<Boolean?, TrustError> =
        catching({ TrustError.WRITE_FAILED }) { Files.createTempFile(configHome, CONFIG_FILE, ".tmp") }
            .flatMap { temp ->
                try {
                    fill(temp, original.permissions, merged).flatMap { commit(temp, original.bytes) }
                } finally {
                    deleteQuietly(temp)
                }
            }

    private fun fill(
        temp: Path,
        permissions: Set<PosixFilePermission>?,
        text: String,
    ): Outcome<Unit, TrustError> =
        catching({ TrustError.WRITE_FAILED }) {
            Files.write(temp, text.toByteArray(Charsets.UTF_8))
            // The data must be on disk before the move, or a crash could leave a renamed empty file.
            FileChannel.open(temp, StandardOpenOption.WRITE).use { it.force(true) }
            if (permissions != null) Files.setPosixFilePermissions(temp, permissions)
        }

    private fun commit(
        temp: Path,
        originalBytes: ByteArray,
    ): Outcome<Boolean?, TrustError> =
        catching({ TrustError.CONFIG_UNREADABLE }) {
            tuning.beforeCommit()
            Files.readAllBytes(file)
        }.flatMap { current ->
            if (!current.contentEquals(originalBytes)) {
                Outcome.Ok(null)
            } else {
                catching({ TrustError.WRITE_FAILED }) {
                    tuning.move(temp, file)
                    true
                }
            }
        }

    private fun deleteQuietly(temp: Path) {
        try {
            Files.deleteIfExists(temp)
        } catch (ignored: IOException) {
            // the content of the temporary file is a copy of the config; there is nothing more to do
        }
    }
}

/** The config as first read: raw bytes (for the re-read comparison), their text and the permissions. */
private class Original(
    val bytes: ByteArray,
    val text: String,
    val permissions: Set<PosixFilePermission>?,
)

/** The reason [file] cannot be edited, null when it can. May throw `IOException`. */
private fun classify(
    file: Path,
    maxBytes: Long,
): TrustError? =
    when {
        !Files.exists(file, LinkOption.NOFOLLOW_LINKS) -> TrustError.CONFIG_MISSING
        !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) -> TrustError.CONFIG_UNSUPPORTED
        Files.size(file) > maxBytes -> TrustError.CONFIG_UNSUPPORTED
        else -> null
    }

/** The bytes of [file] without following a link (a link swapped in after [classify] gives `IOException`). */
private fun readNoFollow(file: Path): ByteArray =
    Files.newByteChannel(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
        Channels.newInputStream(channel).readAllBytes()
    }

/** Permissions of [file]; null on a file system without POSIX permissions. */
private fun permissionsOf(file: Path): Set<PosixFilePermission>? =
    try {
        Files.getPosixFilePermissions(file)
    } catch (ignored: UnsupportedOperationException) {
        null
    }

/** Strict UTF-8: malformed input throws `CharacterCodingException`. */
private fun utf8(bytes: ByteArray): String =
    Charsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

/**
 * Test-only knobs of [ClaudeJsonTrust].
 *
 * @property maxBytes largest config file that is read (16 MiB in production).
 * @property beforeCommit called after the temporary file is written and before the file is read again (the
 * concurrency window); tests change the config here.
 * @property move replaces [target] with [source] atomically (`ATOMIC_MOVE`); throws `IOException` on failure.
 */
internal class TrustTuning(
    val maxBytes: Long = MAX_CONFIG_BYTES,
    val beforeCommit: () -> Unit = {},
    val move: (source: Path, target: Path) -> Unit = ::atomicMove,
)

private fun atomicMove(
    source: Path,
    target: Path,
) {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
}

private const val CONFIG_FILE = ".claude.json"
private const val MAX_ATTEMPTS = 3
private const val MAX_CONFIG_BYTES: Long = 16L * 1024 * 1024
