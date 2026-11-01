package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.HookScope
import dev.quayside.core.domain.Outcome
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

/** Where Claude Code settings live and how they are read and written (atomically). */
internal class SettingsFiles(
    private val homeDir: Path,
) {
    fun of(scope: HookScope): Path =
        when (scope) {
            HookScope.Global -> homeDir.resolve(".claude").resolve("settings.json")
            is HookScope.Project -> scope.dir.resolve(".claude").resolve("settings.local.json")
        }

    // Project labels carry a hash of the directory, so a backup of one project cannot be restored into another.
    fun labelOf(scope: HookScope): String =
        when (scope) {
            HookScope.Global -> "global"
            is HookScope.Project -> "project" + Integer.toHexString(scope.dir.toString().hashCode())
        }

    /** The text of [file], null when it does not exist; `Err` is a fixed category. */
    fun read(file: Path): Outcome<String?, String> =
        try {
            if (file.exists()) Outcome.Ok(String(Files.readAllBytes(file), Charsets.UTF_8)) else Outcome.Ok(null)
        } catch (ignored: IOException) {
            Outcome.Err(SETTINGS_IO_FAILED)
        }

    // Same directory, then move: a crash leaves the old file or the new one, never half of it.
    fun write(
        file: Path,
        text: String,
    ) {
        Files.createDirectories(file.parent)
        val temp = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
        try {
            Files.write(temp, text.toByteArray(Charsets.UTF_8))
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (ignored: AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}

/** Fixed reason of a failed file access; never the file's content. */
internal const val SETTINGS_IO_FAILED = "io"
