package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.BackupRef
import dev.quayside.core.domain.Clock
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/**
 * Copies of settings files made before every install (NFR-5), one file each in [dir] (`hooks-backup/`).
 * A copy is named `<epoch millis>-<number>-<scope label>.json`; the copy of a file that did not exist is an
 * empty `….absent` file. A [BackupRef] is that file name, so a ref can only be used with the scope it was made for.
 * Not thread-safe: the installer calls it under its own lock.
 */
internal class HookBackups(
    private val dir: Path,
    private val clock: Clock,
) {
    private var counter = 0

    /** Saves [original] (null = the file is missing); the copy is readable by the user only. */
    fun save(
        label: String,
        original: String?,
    ): BackupRef {
        Files.createDirectories(dir)
        val suffix = if (original == null) ABSENT else JSON
        val name = "${clock.now().toEpochMilli()}-${counter++}-$label.$suffix"
        val file = dir.resolve(name)
        Files.write(file, original.orEmpty().toByteArray(Charsets.UTF_8))
        restrictToOwner(file)
        return BackupRef(name)
    }

    /** What [ref] holds for [label]: the saved text, [Saved.Absent], or [Saved.Unknown] for any other ref. */
    fun read(
        label: String,
        ref: BackupRef,
    ): Saved {
        val name = ref.value
        val matches = NAME.matches(name) && name.substringBeforeLast('.').endsWith("-$label")
        val file = dir.resolve(name)
        return when {
            !matches || !Files.isRegularFile(file) -> Saved.Unknown
            name.endsWith(".$ABSENT") -> Saved.Absent
            else -> Saved.Text(String(Files.readAllBytes(file), Charsets.UTF_8))
        }
    }

    // POSIX only (macOS): elsewhere the default permissions of the directory apply.
    private fun restrictToOwner(file: Path) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
        } catch (ignored: UnsupportedOperationException) {
            // not a POSIX file system
        } catch (ignored: IOException) {
            // the copy exists; being stricter than the umask is best effort
        }
    }

    private companion object {
        const val JSON = "json"
        const val ABSENT = "absent"
        val NAME = Regex("[0-9]+-[0-9]+-[A-Za-z0-9]+\\.(json|absent)")
    }
}

/** What a backup ref holds, see [HookBackups.read]. */
internal sealed class Saved {
    internal data class Text(
        val text: String,
    ) : Saved()

    internal data object Absent : Saved()

    internal data object Unknown : Saved()
}
