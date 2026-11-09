package dev.quayside.projects.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.testDispatchers
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.ProjectInputRules
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.SubfolderProblem
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * The disk half of `setSubfolders` (FR-P7): a name must be a real directory directly under the project directory.
 * Real files in a temp directory; the checks run on the test io dispatcher.
 */
class SubfolderChecksTest {
    @TempDir
    lateinit var tempDir: Path

    private val base: Path get() = tempDir.toRealPath()

    private val project get() = aProject(id = "p1", dir = Files.createDirectories(base.resolve("plant")))

    private fun mkdir(vararg names: String) = names.forEach { Files.createDirectories(project.dir.resolve(it)) }

    private suspend fun TestScope.resolve(vararg names: String): Outcome<List<SubfolderName>, ProjectError> =
        SubfolderChecks.resolve(project, subfolderNames(*names), testDispatchers().io)

    private fun invalid(
        name: String,
        reason: SubfolderProblem,
    ) = Outcome.Err(ProjectError.InvalidSubfolder(SubfolderName(name), reason))

    @Test
    fun `real directories pass and keep the given order`() =
        runTest {
            mkdir("soft", "docs")

            resolve("soft", "docs") shouldBe Outcome.Ok(subfolderNames("soft", "docs"))
        }

    @Test
    fun `an empty list passes`() =
        runTest {
            resolve() shouldBe Outcome.Ok(emptyList())
        }

    @Test
    fun `repeated names collapse to the first occurrence`() =
        runTest {
            mkdir("soft", "docs")

            resolve("soft", "docs", "soft") shouldBe Outcome.Ok(subfolderNames("soft", "docs"))
        }

    @Test
    fun `missing directory is MISSING`() =
        runTest {
            mkdir("soft")

            resolve("soft", "gone") shouldBe invalid("gone", SubfolderProblem.MISSING)
        }

    @Test
    fun `file is NOT_A_DIRECTORY`() =
        runTest {
            mkdir("soft")
            Files.writeString(project.dir.resolve("notes.txt"), "content")

            resolve("notes.txt") shouldBe invalid("notes.txt", SubfolderProblem.NOT_A_DIRECTORY)
        }

    @Test
    fun `symlink to a directory is SYMLINK`() =
        runTest {
            mkdir("soft")
            val target = Files.createDirectories(base.resolve("elsewhere"))
            Files.createSymbolicLink(project.dir.resolve("linked"), target)

            resolve("linked") shouldBe invalid("linked", SubfolderProblem.SYMLINK)
        }

    @Test
    fun `hidden directory is HIDDEN even when it exists`() =
        runTest {
            mkdir(".git")

            resolve(".git") shouldBe invalid(".git", SubfolderProblem.HIDDEN)
        }

    @Test
    fun `nested name is NOT_A_NAME and the nested directory is not checked`() =
        runTest {
            mkdir("soft/api")

            resolve("soft/api") shouldBe invalid("soft/api", SubfolderProblem.NOT_A_NAME)
        }

    @Test
    fun `the first failing name in the given order is reported`() =
        runTest {
            mkdir("soft")

            resolve("soft", "gone-first", ".hidden-second") shouldBe invalid("gone-first", SubfolderProblem.MISSING)
            resolve(".hidden-first", "gone-second") shouldBe invalid(".hidden-first", SubfolderProblem.HIDDEN)
        }

    @Test
    fun `one more than the limit is TOO_MANY and the limit passes`() =
        runTest {
            val names = (0..ProjectInputRules.MAX_SUBFOLDERS).map { "dir%03d".format(Locale.ROOT, it) }
            mkdir(*names.toTypedArray())

            val accepted = resolve(*names.take(ProjectInputRules.MAX_SUBFOLDERS).toTypedArray())
            val tooMany = resolve(*names.toTypedArray())

            accepted shouldBe Outcome.Ok(names.take(ProjectInputRules.MAX_SUBFOLDERS).map(::SubfolderName))
            (tooMany as Outcome.Err).error.let { it as ProjectError.InvalidSubfolder }.reason shouldBe
                SubfolderProblem.TOO_MANY
        }

    @Test
    fun `repeats do not count against the limit`() =
        runTest {
            val names = (1..ProjectInputRules.MAX_SUBFOLDERS).map { "dir%03d".format(Locale.ROOT, it) }
            mkdir(*names.toTypedArray())

            resolve(*(names + names).toTypedArray()) shouldBe Outcome.Ok(names.map(::SubfolderName))
        }

    @Test
    fun `a name spelled with other case gives the name the disk lists`() =
        runTest {
            mkdir("soft")
            Files.createFile(base.resolve("CaseProbe"))
            assumeTrue(Files.exists(base.resolve("caseprobe")), "the file system tells Soft and soft apart")

            resolve("Soft", "soft") shouldBe Outcome.Ok(subfolderNames("soft"))
        }

    @Test
    fun `a symlink to the directory is never taken for its spelling on disk`() =
        runTest {
            mkdir("soft")
            Files.createSymbolicLink(project.dir.resolve("aaa"), project.dir.resolve("soft"))
            Files.createFile(base.resolve("CaseProbe"))
            assumeTrue(Files.exists(base.resolve("caseprobe")), "the file system tells Soft and soft apart")

            resolve("Soft") shouldBe Outcome.Ok(subfolderNames("soft"))
        }

    @Test
    fun `the spelling on disk is checked against the rules too`() =
        runTest {
            // 41 x "é": 41 code points composed (valid), 82 decomposed (over the limit of 80)
            val composed = "\u00e9".repeat(41)
            val decomposed = "e\u0301".repeat(41)
            Files.createDirectories(project.dir.resolve(decomposed))
            assumeTrue(
                Files.isDirectory(project.dir.resolve(composed)),
                "the file system does not treat composed and decomposed names as one",
            )

            resolve(composed) shouldBe invalid(composed, SubfolderProblem.NOT_A_NAME)
        }
}
