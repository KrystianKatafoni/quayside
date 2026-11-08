package dev.quayside.projects.impl

import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.testing.testDispatchers
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.ProjectInputRules
import dev.quayside.projects.api.ProjectInspector
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.Locale

/**
 * `DirectoryInspector.subfolders` (FR-P7) on real directories in a temp directory. It reads names only: no git
 * process and no runtime call.
 */
class DirectoryInspectorSubfoldersTest {
    @TempDir
    lateinit var tempDir: Path

    private val base: Path get() = tempDir.toRealPath()

    private val process = FakeProcessRunner()

    private fun TestScope.inspector(): ProjectInspector =
        projectInspectorModule(
            projects = FakeProjectsApi(),
            runtime = FakeAgentRuntime(),
            process = process,
            io = testDispatchers().io,
            logger = NoOpLogger,
        )

    private fun plant(vararg children: String): Path {
        val dir = Files.createDirectories(base.resolve("plant"))
        children.forEach { Files.createDirectories(dir.resolve(it)) }
        return dir
    }

    @Test
    fun `lists direct children without hidden ones, files and symlinks`() =
        runTest {
            val dir = plant("soft/api", "docs", ".git")
            Files.writeString(dir.resolve("README.md"), "content")
            Files.createSymbolicLink(dir.resolve("linked"), dir.resolve("docs"))

            inspector().subfolders(dir) shouldBe Outcome.Ok(subfolderNames("docs", "soft"))
        }

    @Test
    fun `names are sorted ignoring case`() =
        runTest {
            val dir = plant("bob", "Anna", "Carl")

            inspector().subfolders(dir) shouldBe Outcome.Ok(subfolderNames("Anna", "bob", "Carl"))
        }

    @Test
    fun `the result is cut at the limit`() =
        runTest {
            val dir =
                plant(
                    *(0 until ProjectInputRules.MAX_SUBFOLDERS + 6)
                        .map {
                            "dir%03d".format(
                                Locale.ROOT,
                                it,
                            )
                        }.toTypedArray(),
                )

            val listed = inspector().subfolders(dir).getOrNull().shouldNotBeNull()

            listed shouldHaveSize ProjectInputRules.MAX_SUBFOLDERS
            listed.map { it.value } shouldBe listed.map { it.value }.sorted()
        }

    @Test
    fun `a directory without read permission is left out`() =
        runTest {
            assumeTrue("posix" in FileSystems.getDefault().supportedFileAttributeViews())
            val dir = plant("open", "locked")
            val locked = dir.resolve("locked")
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"))
            try {
                assumeFalse(Files.isReadable(locked), "running as root: permissions are not enforced")

                inspector().subfolders(dir) shouldBe Outcome.Ok(subfolderNames("open"))
            } finally {
                Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"))
            }
        }

    @Test
    fun `a symlinked project directory lists the real children`() =
        runTest {
            val dir = plant("soft")
            val link = Files.createSymbolicLink(base.resolve("link"), dir)

            inspector().subfolders(link) shouldBe Outcome.Ok(subfolderNames("soft"))
        }

    @Test
    fun `errors name the path as given`() =
        runTest {
            val missing = base.resolve("missing")
            val notes = Files.writeString(base.resolve("notes.txt"), "content")
            val relative = Path.of("projects/a")

            inspector().subfolders(missing) shouldBe Outcome.Err(ProjectError.NotFound(missing))
            inspector().subfolders(notes) shouldBe Outcome.Err(ProjectError.NotADirectory(notes))
            inspector().subfolders(relative) shouldBe Outcome.Err(ProjectError.NotAbsolute(relative))
        }

    @Test
    fun `listing runs no git and does not modify the directory`() =
        runTest {
            val dir = plant("soft")
            val before = Files.list(dir).use { it.map { p -> p.fileName.toString() }.toList() }

            inspector().subfolders(dir)

            process.commands.shouldBeEmpty()
            Files.list(dir).use { it.map { p -> p.fileName.toString() }.toList() } shouldBe before
        }
}
