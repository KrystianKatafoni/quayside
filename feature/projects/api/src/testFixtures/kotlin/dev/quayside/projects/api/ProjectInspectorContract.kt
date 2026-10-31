package dev.quayside.projects.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.getOrNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo

/**
 * Behaviour every [ProjectInspector] must have (ENG-T2). Runs on [FakeProjectInspector] and, from
 * AM-303, on `DirectoryInspector`.
 *
 * The contract creates the empty directory `tempDir/project` before calling [create]; an
 * implementation turns [InspectionWorld] into files, `git init`, a fake runtime and settings in it,
 * and registers [InspectionWorld.registered] in the [ProjectsApi] it reads `alreadyAdded` from.
 * Paths come from the real path of the temp directory, so symlinked temp roots compare equal.
 * Symlink cases need a file system with symbolic links (macOS, Linux).
 */
public abstract class ProjectInspectorContract {
    /** Returns an inspector for which `tempDir/project` looks like [world]. */
    public abstract fun create(
        tempDir: Path,
        world: InspectionWorld,
    ): ProjectInspector

    @TempDir
    protected lateinit var tempDir: Path

    private val base: Path get() = tempDir.toRealPath()

    /** The directory described by the world, `tempDir/project`. */
    protected val projectDir: Path get() = base.resolve("project")

    private fun inspector(world: InspectionWorld = InspectionWorld()): ProjectInspector {
        Files.createDirectories(projectDir)
        return create(base, world)
    }

    private suspend fun ProjectInspector.report(): DirectoryReport = inspect(projectDir).getOrNull().shouldNotBeNull()

    @Test
    public fun `plain directory reports no git, no CLAUDE md, no sessions and no hooks`(): Unit =
        runTest {
            inspector().report() shouldBe
                DirectoryReport(
                    dir = projectDir,
                    git = null,
                    hasClaudeMd = false,
                    sessionCount = 0,
                    hooks = HooksPresence.NONE,
                    alreadyAdded = null,
                )
        }

    @Test
    public fun `CLAUDE md is detected`(): Unit =
        runTest {
            inspector(InspectionWorld(claudeMd = true)).report().hasClaudeMd shouldBe true
        }

    @Test
    public fun `git branch is reported`(): Unit =
        runTest {
            inspector(InspectionWorld(git = GitInfo("main"))).report().git shouldBe GitInfo("main")
        }

    @Test
    public fun `detached head reports a repository without a branch`(): Unit =
        runTest {
            inspector(InspectionWorld(git = GitInfo(branch = null))).report().git shouldBe GitInfo(branch = null)
        }

    @Test
    public fun `session count comes from the runtime`(): Unit =
        runTest {
            inspector(InspectionWorld(sessions = 3)).report().sessionCount shouldBe 3
        }

    @Test
    public fun `unavailable runtime reports no session count`(): Unit =
        runTest {
            inspector(InspectionWorld(sessions = 3, runtimeAvailable = false)).report().sessionCount shouldBe null
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(HooksPresence::class)
    public fun `hooks presence is reported`(hooks: HooksPresence): Unit =
        runTest {
            inspector(InspectionWorld(hooks = hooks)).report().hooks shouldBe hooks
        }

    @Test
    public fun `report names the normalized directory`(): Unit =
        runTest {
            val inspector = inspector()

            inspector
                .inspect(Path.of("$projectDir/./x/.."))
                .getOrNull()
                .shouldNotBeNull()
                .dir shouldBe projectDir
        }

    @Test
    public fun `report names the real path of a symlinked directory`(): Unit =
        runTest {
            val inspector = inspector()
            val link = Files.createSymbolicLink(base.resolve("link"), projectDir)

            inspector
                .inspect(link)
                .getOrNull()
                .shouldNotBeNull()
                .dir shouldBe projectDir
        }

    @Test
    public fun `broken symlink returns NotFound`(): Unit =
        runTest {
            val inspector = inspector()
            val link = Files.createSymbolicLink(base.resolve("broken"), base.resolve("missing"))

            inspector.inspect(link) shouldBe Outcome.Err(ProjectError.NotFound(link))
        }

    @Test
    public fun `symlink loop returns NotFound`(): Unit =
        runTest {
            val inspector = inspector()
            val loop = Files.createSymbolicLink(base.resolve("loop-a"), base.resolve("loop-b"))
            Files.createSymbolicLink(base.resolve("loop-b"), loop)

            inspector.inspect(loop) shouldBe Outcome.Err(ProjectError.NotFound(loop))
        }

    @Test
    public fun `registered directory reports its project`(): Unit =
        runTest {
            val world = InspectionWorld(registered = listOf(aProject(id = REGISTERED, dir = projectDir)))

            inspector(world).report().alreadyAdded shouldBe ProjectId(REGISTERED)
        }

    @Test
    public fun `registered directory reached through a symlink reports its project`(): Unit =
        runTest {
            val inspector = inspector(InspectionWorld(registered = listOf(aProject(id = REGISTERED, dir = projectDir))))
            val link = Files.createSymbolicLink(base.resolve("link"), projectDir)

            inspector
                .inspect(link)
                .getOrNull()
                .shouldNotBeNull()
                .alreadyAdded shouldBe ProjectId(REGISTERED)
        }

    @Test
    public fun `other registered directory is not reported`(): Unit =
        runTest {
            val world = InspectionWorld(registered = listOf(aProject(id = REGISTERED, dir = base.resolve("other"))))

            inspector(world).report().alreadyAdded shouldBe null
        }

    @Test
    public fun `relative path returns NotAbsolute`(): Unit =
        runTest {
            val relative = Path.of("projects/a")

            inspector().inspect(relative) shouldBe Outcome.Err(ProjectError.NotAbsolute(relative))
        }

    @Test
    public fun `missing directory returns NotFound`(): Unit =
        runTest {
            val missing = base.resolve("missing")

            inspector().inspect(missing) shouldBe Outcome.Err(ProjectError.NotFound(missing))
        }

    @Test
    public fun `file instead of directory returns NotADirectory`(): Unit =
        runTest {
            val inspector = inspector()
            val notes = Files.writeString(base.resolve("notes.txt"), "content")

            inspector.inspect(notes) shouldBe Outcome.Err(ProjectError.NotADirectory(notes))
        }

    @Test
    public fun `inspect does not modify the directory`(): Unit =
        runTest {
            val world =
                InspectionWorld(git = GitInfo("main"), claudeMd = true, sessions = 2, hooks = HooksPresence.PARTIAL)
            val inspector = inspector(world)
            val before = snapshot(projectDir)

            inspector.report()

            snapshot(projectDir) shouldBe before
        }

    /** Every entry under [dir] with its last-modified time and, for files, its content. */
    private fun snapshot(dir: Path): Map<String, String> =
        Files.walk(dir).use { paths ->
            paths.toList().associate { path ->
                val content = if (path.isRegularFile()) path.readBytes().contentHashCode().toString() else "dir"
                path.relativeTo(dir).toString() to "$content@${Files.getLastModifiedTime(path)}"
            }
        }

    private companion object {
        const val REGISTERED = "registered"
    }
}
