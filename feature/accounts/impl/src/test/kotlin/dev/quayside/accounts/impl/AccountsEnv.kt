package dev.quayside.accounts.impl

import dev.quayside.accounts.api.AccountPaths
import dev.quayside.accounts.api.AccountsApi
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.TestClock
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions

/**
 * A fake user on a real temp directory: `home` (with `.claude`), the app support dir with `accounts.json`
 * and `accounts-backup`. Never the real `~` (ENG-T3).
 */
class AccountsEnv(
    root: Path,
    private val io: CoroutineDispatcher,
) {
    val home: Path = root.resolve("home")
    val support: Path = root.resolve("support")
    val backups: Path = support.resolve("accounts-backup")
    val defaultDir: Path = home.resolve(".claude")
    val file: Path = support.resolve("accounts.json")
    val clock = TestClock()
    val logger = RecordingLogger()

    init {
        Files.createDirectories(defaultDir)
    }

    suspend fun open(): AccountsApi =
        accountsModule(JsonStorage(support, io, logger), AccountPaths(home, backups), clock, io, logger)

    /** Writes `accounts.json` as the store would: `{"schemaVersion": [version], "data": [data]}`. */
    fun writeFile(
        data: String,
        version: Int = 1,
    ) {
        Files.createDirectories(support)
        Files.writeString(file, """{"schemaVersion": $version, "data": $data}""")
    }

    /** Makes every later save of the account list fail: the target is a non-empty directory (the move fails). */
    fun breakRegistry() {
        Files.deleteIfExists(file)
        Files.createDirectories(file.resolve("keep"))
        Files.writeString(file.resolve("keep").resolve("x"), "x")
    }

    /** Writes [text] to [relative] under [root], creating directories. */
    fun put(
        root: Path,
        relative: String,
        text: String = relative,
    ): Path {
        val target = root.resolve(relative)
        Files.createDirectories(target.parent)
        return Files.writeString(target, text)
    }

    /** `~/.claude` with all five copyable items and everything that must never be copied. */
    fun fillDefault() {
        put(defaultDir, "CLAUDE.md", "instructions")
        put(defaultDir, "settings.json", "{}")
        put(defaultDir, "agents/reviewer.md", "agent")
        put(defaultDir, "skills/deploy/SKILL.md", "skill")
        put(defaultDir, "commands/ship.md", "command")
        put(defaultDir, "plugins/p/plugin.json", "{}")
        put(defaultDir, ".claude.json", "{\"oauth\": 1}")
        put(defaultDir, ".credentials.json", "secret")
        put(defaultDir, "daemon/state.json", "{}")
        put(defaultDir, "jobs/job.json", "{}")
        put(defaultDir, "projects/p/s.jsonl", "transcript")
    }

    fun names(dir: Path): Set<String> = Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList().toSet() }

    fun read(path: Path): String = Files.readString(path)

    fun permissions(path: Path): String = PosixFilePermissions.toString(Files.getPosixFilePermissions(path))

    fun mtime(path: Path): FileTime = Files.getLastModifiedTime(path)

    companion object {
        fun assumePosix() = assumeTrue(Files.getFileStore(Path.of("/")).supportsFileAttributeView("posix"))

        /** `chmod` tests prove nothing as root (root reads everything). */
        fun assumePosixNotRoot() {
            assumePosix()
            assumeTrue(System.getProperty("user.name") != "root")
        }
    }
}
