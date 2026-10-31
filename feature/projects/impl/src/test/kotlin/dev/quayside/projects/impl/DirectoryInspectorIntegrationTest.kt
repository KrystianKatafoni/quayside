package dev.quayside.projects.impl

import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.HookKind
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.process.LoginShellProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.process.ProcessRunner
import dev.quayside.core.process.RunOptions
import dev.quayside.projects.api.DirectoryReport
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.GitInfo
import dev.quayside.projects.api.HooksPresence
import dev.quayside.projects.api.PathKind
import dev.quayside.projects.api.ProjectInspector
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/**
 * `projectInspectorModule` on the real `git` (through the login-shell runner) and real files in a temp
 * directory (docs/12: adapters on local resources). Only the runtime is fake. Repositories are created by
 * [TestGit] in an isolated git environment; nothing waits on wall-clock time.
 */
class DirectoryInspectorIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    private val base: Path get() = tempDir.toRealPath()

    private val git by lazy { TestGit(home = base) }

    // Real threads: git processes and file IO block, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    private fun inspector(
        gitExecutable: String = "git",
        process: ProcessRunner = LoginShellProcessRunner(io, shell = Path.of("/bin/sh")),
    ): ProjectInspector =
        projectInspectorModule(
            projects = FakeProjectsApi(pathKind = PathKind::onDisk, realPath = PathKind::realPathOnDisk),
            runtime = FakeAgentRuntime(),
            process = process,
            io = io,
            logger = NoOpLogger,
            gitExecutable = gitExecutable,
        )

    private suspend fun report(
        dir: Path,
        gitExecutable: String = "git",
    ): DirectoryReport = inspector(gitExecutable).inspect(dir).getOrNull().shouldNotBeNull()

    private fun dir(name: String): Path = base.resolve(name).createDirectories()

    private fun repo(
        name: String = "repo",
        branch: String = "main",
    ): Path = dir(name).also { git.init(it, branch) }

    private fun settingsFile(
        dir: Path,
        name: String = DiskWorld.LOCAL,
    ): Path = dir.resolve(".claude").createDirectories().resolve(name)

    @Test
    fun `subdirectory of a repository reports its branch`() =
        runTest {
            val sub = repo(branch = "develop").resolve("src/deep").createDirectories()

            report(sub).git shouldBe GitInfo("develop")
        }

    @Test
    fun `linked worktree reports its branch`() =
        runTest {
            val main = repo()
            git.commitAll(main)
            git.run(main, "worktree", "add", "--quiet", "-b", "wt-branch", base.resolve("worktree").toString())

            report(base.resolve("worktree")).git shouldBe GitInfo("wt-branch")
        }

    @Test
    fun `unborn branch right after git init is reported`() =
        runTest {
            report(repo(branch = "feature/x")).git shouldBe GitInfo("feature/x")
        }

    @Test
    fun `git dir itself is not a work tree`() =
        runTest {
            report(repo().resolve(".git")).git.shouldBeNull()
        }

    @Test
    fun `missing git gives no git info`() =
        runTest {
            report(repo(), gitExecutable = "git-am-missing-${UUID.randomUUID()}").git.shouldBeNull()
        }

    @Test
    fun `modified tracked file leaves the index untouched`() =
        runTest {
            val repo = repo()
            val tracked = repo.resolve("tracked.txt")
            tracked.writeText("first\n")
            git.commitAll(repo)
            tracked.writeText("second, longer\n")
            Files.setLastModifiedTime(
                tracked,
                FileTime.fromMillis(Files.getLastModifiedTime(tracked).toMillis() + 60_000),
            )
            val index = repo.resolve(".git/index")
            val bytes = index.readBytes()
            val modified = Files.getLastModifiedTime(index)

            report(repo).git shouldBe GitInfo("main")

            index.readBytes().contentEquals(bytes) shouldBe true
            Files.getLastModifiedTime(index) shouldBe modified
            repo.resolve(".git/index.lock").shouldNotExist()
        }

    /**
     * `rev-parse --is-inside-work-tree` and `symbolic-ref` do not read the index, so this test alone would
     * also pass without `core.fsmonitor=false`; the flag itself is pinned by [GitCommandsTest].
     */
    @Test
    fun `repository fsmonitor command is never run`() =
        runTest {
            val repo = repo()
            val marker = base.resolve("fsmonitor-ran")
            val script = base.resolve("fsmonitor.sh")
            script.writeText("#!/bin/sh\ntouch '$marker'\n")
            Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"))
            git.run(repo, "config", "core.fsmonitor", script.toString())
            repo.resolve("file.txt").writeText("content\n")
            git.commitAll(repo)
            Files.deleteIfExists(marker)

            report(repo).git shouldBe GitInfo("main")

            marker.shouldNotExist()
        }

    @Test
    fun `git planted in the inspected dir is never run`() =
        runTest {
            val repo = repo()
            val marker = base.resolve("planted-git-ran")
            val planted = repo.resolve("git")
            planted.writeText("#!/bin/sh\ntouch '$marker'\nexit 1\n")
            Files.setPosixFilePermissions(planted, PosixFilePermissions.fromString("rwxr-xr-x"))
            // The login profile puts the current directory first on PATH, as a careless user setup would.
            base.resolve(".profile").writeText("PATH=\".:\$PATH\"; export PATH\n")
            val hostile = HermeticHomeRunner(LoginShellProcessRunner(io, shell = Path.of("/bin/sh")), home = base)
            // Self-check of the trap: a command started in the inspected dir does run the planted git.
            hostile.run(ShellCommand(listOf("git", "--version"), repo))
            marker.shouldExist()
            Files.delete(marker)

            val report = inspector(process = hostile).inspect(repo).getOrNull().shouldNotBeNull()

            report.git shouldBe GitInfo("main")
            marker.shouldNotExist()
        }

    @Test
    fun `settings file that is a directory is UNREADABLE`() =
        runTest {
            val dir = dir("project")
            settingsFile(dir, DiskWorld.SHARED).createDirectories()

            report(dir).hooks shouldBe HooksPresence.UNREADABLE
        }

    @Test
    fun `settings file over 1 MiB is UNREADABLE`() =
        runTest {
            val dir = dir("project")
            settingsFile(dir).writeText(padded(HookSettings.settings(HookKind.entries), SettingsFiles.MAX_BYTES + 1))

            report(dir).hooks shouldBe HooksPresence.UNREADABLE
        }

    @Test
    fun `settings file of exactly 1 MiB is read`() =
        runTest {
            val dir = dir("project")
            settingsFile(dir).writeText(padded(HookSettings.settings(HookKind.entries), SettingsFiles.MAX_BYTES))

            report(dir).hooks shouldBe HooksPresence.INSTALLED
        }

    @Test
    fun `settings with invalid UTF-8 is UNREADABLE`() =
        runTest {
            val dir = dir("project")
            val valid = HookSettings.settings(HookKind.entries).toByteArray()
            // 0xC3 0x28 is an invalid two-byte sequence, placed inside a JSON string value.
            settingsFile(dir).writeBytes(
                """{"name": """".toByteArray() + byteArrayOf(0xC3.toByte(), 0x28) + "\", ".toByteArray() +
                    valid.copyOfRange(1, valid.size),
            )

            report(dir).hooks shouldBe HooksPresence.UNREADABLE
        }

    @Test
    fun `settings without read permission is UNREADABLE`() =
        runTest {
            val dir = dir("project")
            val file = settingsFile(dir)
            file.writeText(HookSettings.settings(HookKind.entries))
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"))
            try {
                assumeFalse(Files.isReadable(file), "running as root: permissions are not enforced")

                report(dir).hooks shouldBe HooksPresence.UNREADABLE
            } finally {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"))
            }
        }

    @Test
    fun `hooks in both files are combined`() =
        runTest {
            val dir = dir("project")
            settingsFile(
                dir,
                DiskWorld.SHARED,
            ).writeText(HookSettings.settings(listOf(HookKind.STOP, HookKind.NOTIFICATION)))
            settingsFile(dir, DiskWorld.LOCAL)
                .writeText(
                    HookSettings.settings(listOf(HookKind.STOP_FAILURE, HookKind.SESSION_START, HookKind.SESSION_END)),
                )

            report(dir).hooks shouldBe HooksPresence.INSTALLED
        }

    @Test
    fun `symlinked CLAUDE md counts`() =
        runTest {
            val dir = dir("project")
            val shared = base.resolve("shared.md").also { it.writeText("# Shared\n") }
            Files.createSymbolicLink(dir.resolve("CLAUDE.md"), shared)

            report(dir).hasClaudeMd shouldBe true
        }

    @Test
    fun `CLAUDE md directory does not count`() =
        runTest {
            val dir = dir("project")
            dir.resolve("CLAUDE.md").createDirectories()
            dir.resolve(".claude/CLAUDE.md").createDirectories()

            report(dir).hasClaudeMd shouldBe false
        }

    @Test
    fun `dot claude CLAUDE md counts`() =
        runTest {
            val dir = dir("project")
            dir
                .resolve(".claude")
                .createDirectories()
                .resolve("CLAUDE.md")
                .writeText("# Project\n")

            report(dir).hasClaudeMd shouldBe true
        }

    /** [json] followed by spaces up to exactly [size] bytes (UTF-8). */
    private fun padded(
        json: String,
        size: Int,
    ): String = json + " ".repeat(size - json.toByteArray().size)

    /** Runs every command with `HOME` (and `ZDOTDIR`) set to [home], so the login shell reads only its profile. */
    private class HermeticHomeRunner(
        private val delegate: ProcessRunner,
        private val home: Path,
    ) : ProcessRunner {
        override suspend fun run(
            command: ShellCommand,
            options: RunOptions,
        ): Outcome<ProcessResult, ProcessError> =
            delegate.run(
                command.copy(env = command.env + mapOf("HOME" to home.toString(), "ZDOTDIR" to home.toString())),
                options,
            )
    }
}
