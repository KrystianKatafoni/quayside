package dev.agentmanager.projects.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.getOrNull
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

/**
 * Path half of [ProjectsApiContract]: which directory [ProjectsApi.add] accepts and which path it
 * stores (normalization, symlinks, missing paths). Implementations extend [ProjectsApiContract],
 * which runs these cases too; this class only keeps the contract file small.
 *
 * Builds real directories in a fresh JUnit temp directory; paths come from its real path, so
 * symlinked temp roots (macOS `/var`) compare equal. Symlink cases need a file system with symbolic
 * links (macOS, Linux).
 */
public abstract class ProjectsApiPathContract {
    /** Returns an empty [ProjectsApi]; [tempDir] is a fresh temp directory it may use for storage. */
    public abstract fun create(tempDir: Path): ProjectsApi

    @TempDir
    protected lateinit var tempDir: Path

    /** Root for the directories of a test, under the real path of [tempDir]. */
    protected val root: Path get() = tempDir.toRealPath().resolve("workspace")

    /** Creates the directory [relative] under [root] with its parents. */
    protected fun directory(relative: String): Path = Files.createDirectories(root.resolve(relative))

    /** Creates the file [relative] under [root] containing `content`. */
    protected fun file(relative: String): Path {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        return Files.writeString(path, "content")
    }

    /** Creates the symlink [relative] under [root] pointing at [target]. */
    protected fun symlink(
        relative: String,
        target: Path,
    ): Path {
        val link = root.resolve(relative)
        Files.createDirectories(link.parent)
        return Files.createSymbolicLink(link, target)
    }

    /** A fresh implementation under test. */
    protected fun api(): ProjectsApi = create(tempDir)

    /** Adds [dir] and fails the test unless it succeeds. */
    protected suspend fun ProjectsApi.added(
        dir: Path,
        displayName: String? = null,
    ): Project = add(dir, displayName).getOrNull().shouldNotBeNull()

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["a/./b", "a/b/../b"])
    public fun `adding the same directory through a different spelling returns AlreadyAdded`(spelling: String): Unit =
        runTest {
            val api = api()
            val first = api.added(directory("a/b"))

            api.add(Path.of("$root/$spelling")) shouldBe Outcome.Err(ProjectError.AlreadyAdded(first.id))

            api.projects.value shouldContainExactly listOf(first)
        }

    @Test
    public fun `added directory is stored normalized`(): Unit =
        runTest {
            val dir = directory("a/b")

            api().added(Path.of("$root/a/./c/../b")).dir shouldBe dir
        }

    @Test
    public fun `adding a directory through a symlink returns AlreadyAdded when its target is added`(): Unit =
        runTest {
            val api = api()
            val target = directory("real")
            val first = api.added(target)

            api.add(symlink("link", target)) shouldBe Outcome.Err(ProjectError.AlreadyAdded(first.id))

            api.projects.value shouldContainExactly listOf(first)
        }

    @Test
    public fun `adding the target of an added symlink returns AlreadyAdded`(): Unit =
        runTest {
            val api = api()
            val target = directory("real")
            val first = api.added(symlink("link", target))

            api.add(target) shouldBe Outcome.Err(ProjectError.AlreadyAdded(first.id))
        }

    @Test
    public fun `adding through a symlink stores the real path`(): Unit =
        runTest {
            val target = directory("real")

            api().added(symlink("link", target)).dir shouldBe target
        }

    @Test
    public fun `directory under a symlinked parent is stored by its real path`(): Unit =
        runTest {
            val child = directory("real-parent/child")
            symlink("alias", child.parent)

            api().added(root.resolve("alias/child")).dir shouldBe child
        }

    @Test
    public fun `broken symlink returns NotFound`(): Unit =
        runTest {
            val api = api()
            val link = symlink("broken", root.resolve("missing"))

            api.add(link) shouldBe Outcome.Err(ProjectError.NotFound(link))

            api.projects.value.shouldBeEmpty()
        }

    @Test
    public fun `symlink loop returns NotFound`(): Unit =
        runTest {
            val api = api()
            val loop = symlink("loop-a", root.resolve("loop-b"))
            symlink("loop-b", loop)

            api.add(loop) shouldBe Outcome.Err(ProjectError.NotFound(loop))

            api.projects.value.shouldBeEmpty()
        }

    @Test
    public fun `symlink to a file returns NotADirectory`(): Unit =
        runTest {
            val link = symlink("notes-link", file("notes.txt"))

            api().add(link) shouldBe Outcome.Err(ProjectError.NotADirectory(link))
        }

    @Test
    public fun `relative path returns NotAbsolute`(): Unit =
        runTest {
            val api = api()
            val relative = Path.of("projects/a")

            api.add(relative) shouldBe Outcome.Err(ProjectError.NotAbsolute(relative))

            api.projects.value.shouldBeEmpty()
        }

    @Test
    public fun `missing directory returns NotFound`(): Unit =
        runTest {
            val api = api()
            val missing = root.resolve("missing")

            api.add(missing) shouldBe Outcome.Err(ProjectError.NotFound(missing))

            api.projects.value.shouldBeEmpty()
        }

    @Test
    public fun `file instead of directory returns NotADirectory`(): Unit =
        runTest {
            val api = api()
            val notes = file("notes.txt")

            api.add(notes) shouldBe Outcome.Err(ProjectError.NotADirectory(notes))

            api.projects.value.shouldBeEmpty()
        }
}
