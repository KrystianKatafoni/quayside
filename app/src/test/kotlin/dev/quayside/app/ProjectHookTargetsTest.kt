package dev.quayside.app

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting

/** The targets of the hooks dialog over the project list, on real folders in a temp directory (AM-609). */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectHookTargetsTest {
    @TempDir
    lateinit var root: Path

    private fun folder(name: String): Path = root.resolve(name).createDirectories()

    @Test
    fun `global comes first and projects keep the sidebar order`() =
        runTest {
            val zed = Project(ProjectId("p1"), folder("zed"), "Zed")
            val anna = Project(ProjectId("p2"), folder("anna"), "anna")
            val api = FakeProjectsApi(listOf(zed, anna))
            val targets = ProjectHookTargets(api.projects, UnconfinedTestDispatcher(testScheduler))

            targets.targets() shouldBe
                listOf(
                    HookTarget.Global,
                    HookTarget.Project(zed.id, "Zed", zed.dir, present = true),
                    HookTarget.Project(anna.id, "anna", anna.dir, present = true),
                )
        }

    @Test
    fun `a deleted project folder is marked missing`() =
        runTest {
            val kept = Project(ProjectId("p1"), folder("kept"), "kept")
            val deleted = Project(ProjectId("p2"), folder("deleted"), "deleted")
            val api = FakeProjectsApi(listOf(kept, deleted))
            val targets = ProjectHookTargets(api.projects, UnconfinedTestDispatcher(testScheduler))
            deleted.dir.deleteExisting()

            targets.targets().map { (it as? HookTarget.Project)?.present } shouldBe listOf(null, true, false)
        }

    @Test
    fun `a file in place of the folder is marked missing`() =
        runTest {
            val project = Project(ProjectId("p1"), root.resolve("file"), "file")
            root.resolve("file").toFile().writeText("x")
            val api = FakeProjectsApi(listOf(project))
            val targets = ProjectHookTargets(api.projects, UnconfinedTestDispatcher(testScheduler))

            (targets.targets().last() as HookTarget.Project).present shouldBe false
        }

    @Test
    fun `the list is read when targets is called and not when the source is built`() =
        runTest {
            val api = FakeProjectsApi()
            val targets = ProjectHookTargets(api.projects, UnconfinedTestDispatcher(testScheduler))
            api.add(folder("late"), displayName = null)

            targets.targets().size shouldBe 2
        }

    @Test
    fun `the given folder check decides whether a project is present`() =
        runTest {
            val project = Project(ProjectId("p1"), Path.of("/work/never-on-disk"), "x")
            val api = FakeProjectsApi(listOf(project))
            val seen = ArrayList<Path>()
            val targets =
                ProjectHookTargets(api.projects, UnconfinedTestDispatcher(testScheduler)) {
                    seen.add(it)
                    true
                }

            (targets.targets().last() as HookTarget.Project).present shouldBe true
            seen shouldBe listOf(project.dir)
        }
}
