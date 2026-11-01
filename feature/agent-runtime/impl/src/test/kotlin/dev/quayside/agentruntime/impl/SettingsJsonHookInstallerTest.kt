package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.BackupRef
import dev.quayside.agentruntime.api.HookError
import dev.quayside.agentruntime.api.HookInstaller
import dev.quayside.agentruntime.api.HookKind
import dev.quayside.agentruntime.api.HookScope
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.testing.TestClock
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** What the file-based installer does to real files (NFR-5): backups, foreign content, failures. */
@Suppress("InjectDispatcher")
class SettingsJsonHookInstallerTest {
    @TempDir
    lateinit var dir: Path

    private val home get() = dir.resolve("home")
    private val globalFile get() = home.resolve(".claude/settings.json")
    private val backupDir get() = dir.resolve("hooks-backup")
    private val projectDir get() = dir.resolve("project")
    private val localFile get() = projectDir.resolve(".claude/settings.local.json")

    private fun installer(): HookInstaller =
        hookInstallerModule(home, backupDir, TestClock(), Dispatchers.IO, NoOpLogger)

    private fun write(
        file: Path,
        text: String,
    ) {
        file.parent.createDirectories()
        file.writeText(text)
    }

    private val foreign =
        """{"model": "opus", "hooks": {"Stop": [{"hooks": [{"type": "command", "command": "x.sh"}]}]}}"""

    @Test
    fun `install adds our hooks and keeps everything else`() =
        runTest {
            write(globalFile, foreign)

            installer().install(HookScope.Global).shouldBeInstanceOf<Outcome.Ok<BackupRef>>()

            val text = globalFile.readText()
            text.contains("\"model\": \"opus\"") shouldBe true
            text.contains("x.sh") shouldBe true
            installer().status(HookScope.Global) shouldBe HookStatus.Installed
        }

    @Test
    fun `install creates a missing settings file and its directory`() =
        runTest {
            installer().install(HookScope.Global)

            globalFile.exists() shouldBe true
        }

    @Test
    fun `the backup holds the original text and is private`() =
        runTest {
            write(globalFile, foreign)

            val ref = installer().install(HookScope.Global).getOrNull().shouldNotBeNull()

            backupDir.resolve(ref.value).readText() shouldBe foreign
            val permissions = Files.getPosixFilePermissions(backupDir.resolve(ref.value))
            permissions.map { it.name }.toSet() shouldBe setOf("OWNER_READ", "OWNER_WRITE")
        }

    @Test
    fun `a second install leaves the file as it is and adds another backup`() =
        runTest {
            val installer = installer()
            installer.install(HookScope.Global)
            val installed = globalFile.readText()

            installer.install(HookScope.Global)

            globalFile.readText() shouldBe installed
            backupDir.listDirectoryEntries() shouldHaveSize 2
        }

    @Test
    fun `restoring the backup brings the original back byte for byte`() =
        runTest {
            write(globalFile, foreign)
            val installer = installer()
            val ref = installer.install(HookScope.Global).getOrNull().shouldNotBeNull()

            installer.uninstall(HookScope.Global, ref) shouldBe Outcome.Ok(Unit)

            globalFile.readText() shouldBe foreign
        }

    @Test
    fun `restoring the backup of a file that did not exist removes the file`() =
        runTest {
            val installer = installer()
            val ref = installer.install(HookScope.Global).getOrNull().shouldNotBeNull()

            installer.uninstall(HookScope.Global, ref) shouldBe Outcome.Ok(Unit)

            globalFile.exists() shouldBe false
        }

    @Test
    fun `uninstall without a backup removes only our hooks`() =
        runTest {
            write(globalFile, foreign)
            val installer = installer()
            installer.install(HookScope.Global)

            installer.uninstall(HookScope.Global, backup = null) shouldBe Outcome.Ok(Unit)

            val text = globalFile.readText()
            text.contains("x.sh") shouldBe true
            text.contains("X-AM-Token") shouldBe false
        }

    @Test
    fun `uninstall of a missing file is fine`() =
        runTest {
            installer().uninstall(HookScope.Global, backup = null) shouldBe Outcome.Ok(Unit)
        }

    @Test
    fun `a backup cannot be used for another scope or with a path`() =
        runTest {
            val installer = installer()
            val ref = installer.install(HookScope.Global).getOrNull().shouldNotBeNull()
            val project = HookScope.Project(projectDir)

            installer.uninstall(project, ref) shouldBe Outcome.Err(HookError.BackupNotFound(ref))
            val traversal = BackupRef("../home/.claude/settings.json")
            installer.uninstall(HookScope.Global, traversal) shouldBe Outcome.Err(HookError.BackupNotFound(traversal))
        }

    @Test
    fun `project scope writes the local settings and leaves the shared ones alone`() =
        runTest {
            write(projectDir.resolve(".claude/settings.json"), foreign)

            installer().install(HookScope.Project(projectDir))

            localFile.exists() shouldBe true
            projectDir.resolve(".claude/settings.json").readText() shouldBe foreign
        }

    @Test
    fun `status is partial when some kinds are missing`() =
        runTest {
            val handler = """{"type": "http", "url": "http://127.0.0.1:1/hooks", "headers": {"X-AM-Token": "t"}}"""
            write(globalFile, """{"hooks": {"Stop": [{"hooks": [$handler]}]}}""")

            installer().status(HookScope.Global) shouldBe HookStatus.Partial(HookKind.entries.toSet() - HookKind.STOP)
        }

    @Test
    fun `an unusable file is reported by category, left untouched and never quoted`() =
        runTest {
            write(globalFile, "{oops secret-user")
            val installer = installer()

            installer.status(HookScope.Global) shouldBe HookStatus.Unreadable(SettingsProblem.INVALID_JSON)
            installer.install(HookScope.Global) shouldBe
                Outcome.Err(HookError.SettingsUnreadable(globalFile, SettingsProblem.INVALID_JSON))
            installer.uninstall(HookScope.Global, null) shouldBe
                Outcome.Err(HookError.SettingsUnreadable(globalFile, SettingsProblem.INVALID_JSON))

            globalFile.readText() shouldBe "{oops secret-user"
            backupDir.exists() shouldBe false
        }

    @Test
    fun `a settings path that is a directory is an unreadable file, not a crash`() =
        runTest {
            globalFile.createDirectories()

            installer().status(HookScope.Global).shouldBeInstanceOf<HookStatus.Unreadable>()
            installer().install(HookScope.Global).shouldBeInstanceOf<Outcome.Err<HookError.SettingsUnreadable>>()
        }

    @Test
    fun `a backup directory that cannot be created is a write failure and the file stays`() =
        runTest {
            write(globalFile, foreign)
            Files.writeString(backupDir, "a file where the directory should be")

            val result = installer().install(HookScope.Global)

            result shouldBe Outcome.Err(HookError.WriteFailed(globalFile, "io"))
            globalFile.readText() shouldBe foreign
        }
}
