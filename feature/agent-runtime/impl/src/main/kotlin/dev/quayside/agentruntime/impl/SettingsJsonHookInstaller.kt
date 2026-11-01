package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.BackupRef
import dev.quayside.agentruntime.api.HookError
import dev.quayside.agentruntime.api.HookInstaller
import dev.quayside.agentruntime.api.HookKind
import dev.quayside.agentruntime.api.HookScope
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.debug
import dev.quayside.core.domain.flatMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * [HookInstaller] on the real settings files (NFR-5, docs/03 "Hooki"): `~/.claude/settings.json` for
 * [HookScope.Global], `<dir>/.claude/settings.local.json` for [HookScope.Project]. Edits only our own
 * handlers (see [HookSettingsJson]), backs the file up first and writes atomically. Everything runs on [io]
 * under one lock, so two calls never interleave. Reasons in errors are fixed categories, never file content.
 */
internal class SettingsJsonHookInstaller(
    private val files: SettingsFiles,
    private val backups: HookBackups,
    private val port: Int,
    private val io: CoroutineDispatcher,
    private val logger: Logger,
) : HookInstaller {
    private val lock = Mutex()

    override suspend fun status(scope: HookScope): HookStatus =
        locked {
            val file = files.of(scope)
            when (val read = files.read(file)) {
                is Outcome.Err -> HookStatus.Unreadable(read.error)
                is Outcome.Ok -> statusOf(read.value?.let(HookSettingsJson::parse))
            }
        }

    override suspend fun install(scope: HookScope): Outcome<BackupRef, HookError> =
        locked {
            val file = files.of(scope)
            files.read(file).toHookError(file).flatMap { text -> installInto(scope, file, text) }
        }

    override suspend fun uninstall(
        scope: HookScope,
        backup: BackupRef?,
    ): Outcome<Unit, HookError> =
        locked {
            val file = files.of(scope)
            if (backup != null) restore(scope, file, backup) else removeOurs(file)
        }

    private suspend fun <T> locked(block: () -> T): T = lock.withLock { withContext(io) { block() } }

    private fun statusOf(parsed: Outcome<JsonObject, String>?): HookStatus =
        when (parsed) {
            null -> {
                HookStatus.NotInstalled
            }

            is Outcome.Err -> {
                HookStatus.Unreadable(parsed.error)
            }

            is Outcome.Ok -> {
                val found = HookSettingsJson.kinds(parsed.value)
                val missing = HookKind.entries.toSet() - found
                when {
                    found.isEmpty() -> HookStatus.NotInstalled
                    missing.isEmpty() -> HookStatus.Installed
                    else -> HookStatus.Partial(missing)
                }
            }
        }

    private fun installInto(
        scope: HookScope,
        file: Path,
        text: String?,
    ): Outcome<BackupRef, HookError> {
        val parsed = text?.let(HookSettingsJson::parse) ?: Outcome.Ok(JsonObject(emptyMap()))
        return when (parsed) {
            is Outcome.Err -> {
                Outcome.Err(HookError.SettingsUnreadable(file, parsed.error))
            }

            is Outcome.Ok -> {
                val updated = HookSettingsJson.withOurHooks(parsed.value, port)
                val changed = updated !== parsed.value
                io(file) {
                    val ref = backups.save(files.labelOf(scope), text)
                    if (text == null || changed) files.write(file, HookSettingsJson.render(updated))
                    logger.debug("Hooks installed", "scope" to files.labelOf(scope), "changed" to changed)
                    ref
                }
            }
        }
    }

    private fun removeOurs(file: Path): Outcome<Unit, HookError> =
        files.read(file).toHookError(file).flatMap { text ->
            val parsed = text?.let(HookSettingsJson::parse)
            when (parsed) {
                null -> {
                    Outcome.Ok(Unit)
                }

                is Outcome.Err -> {
                    Outcome.Err(HookError.SettingsUnreadable(file, parsed.error))
                }

                is Outcome.Ok -> {
                    val cleaned = HookSettingsJson.withoutOurHooks(parsed.value)
                    if (cleaned ===
                        parsed.value
                    ) {
                        Outcome.Ok(Unit)
                    } else {
                        io(file) { files.write(file, HookSettingsJson.render(cleaned)) }
                    }
                }
            }
        }

    private fun restore(
        scope: HookScope,
        file: Path,
        ref: BackupRef,
    ): Outcome<Unit, HookError> =
        try {
            when (val saved = backups.read(files.labelOf(scope), ref)) {
                Saved.Unknown -> Outcome.Err(HookError.BackupNotFound(ref))
                Saved.Absent -> io(file) { Files.deleteIfExists(file) }.mapUnit()
                is Saved.Text -> io(file) { files.write(file, saved.text) }
            }
        } catch (ignored: IOException) {
            Outcome.Err(HookError.WriteFailed(file, SETTINGS_IO_FAILED))
        }

    private fun Outcome<String?, String>.toHookError(file: Path): Outcome<String?, HookError> =
        when (this) {
            is Outcome.Ok -> this
            is Outcome.Err -> Outcome.Err(HookError.SettingsUnreadable(file, error))
        }

    private fun <T> io(
        file: Path,
        block: () -> T,
    ): Outcome<T, HookError> =
        try {
            Outcome.Ok(block())
        } catch (ignored: IOException) {
            Outcome.Err(HookError.WriteFailed(file, SETTINGS_IO_FAILED))
        } catch (ignored: SecurityException) {
            Outcome.Err(HookError.WriteFailed(file, SETTINGS_IO_FAILED))
        }

    private fun Outcome<Boolean, HookError>.mapUnit(): Outcome<Unit, HookError> =
        when (this) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Err -> this
        }
}
