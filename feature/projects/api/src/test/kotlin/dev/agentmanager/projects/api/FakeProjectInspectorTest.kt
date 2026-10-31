package dev.agentmanager.projects.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.getOrNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

class FakeProjectInspectorTest {
    private val otherDir = Path.of("/Users/test/projects/other")

    @Test
    fun `alreadyAdded refers to a project from the given ProjectsApi`() =
        runTest {
            val projects = FakeProjectsApi(initial = listOf(aProject(id = "registered", dir = A_PROJECT_DIR)))
            val inspector = FakeProjectInspector(projects = projects)

            inspector
                .inspect(A_PROJECT_DIR)
                .getOrNull()
                .shouldNotBeNull()
                .alreadyAdded shouldBe ProjectId("registered")
            inspector
                .inspect(otherDir)
                .getOrNull()
                .shouldNotBeNull()
                .alreadyAdded shouldBe null
        }

    @Test
    fun `without a ProjectsApi nothing is already added`() =
        runTest {
            FakeProjectInspector()
                .inspect(A_PROJECT_DIR)
                .getOrNull()
                .shouldNotBeNull()
                .alreadyAdded shouldBe null
        }

    @Test
    fun `default fake reports the world for paths that do not exist on disk`() =
        runTest {
            val world = InspectionWorld(git = GitInfo("main"), claudeMd = true, sessions = 2)

            FakeProjectInspector(world).inspect(A_PROJECT_DIR) shouldBe
                Outcome.Ok(
                    DirectoryReport(
                        dir = A_PROJECT_DIR,
                        git = GitInfo("main"),
                        hasClaudeMd = true,
                        sessionCount = 2,
                        hooks = HooksPresence.NONE,
                        alreadyAdded = null,
                    ),
                )
        }

    @Test
    fun `report and alreadyAdded use the real path`() =
        runTest {
            val link = Path.of("/Users/test/link")
            val projects = FakeProjectsApi(initial = listOf(aProject(id = "registered", dir = A_PROJECT_DIR)))
            val inspector =
                FakeProjectInspector(projects = projects, realPath = { if (it == link) A_PROJECT_DIR else it })

            val report = inspector.inspect(link).getOrNull().shouldNotBeNull()

            report.dir shouldBe A_PROJECT_DIR
            report.alreadyAdded shouldBe ProjectId("registered")
        }

    @Test
    fun `unresolvable path returns NotFound`() =
        runTest {
            FakeProjectInspector(realPath = { null }).inspect(A_PROJECT_DIR) shouldBe
                Outcome.Err(ProjectError.NotFound(A_PROJECT_DIR))
        }

    @Test
    fun `without a ProjectsApi the registered projects of the world are already added`() =
        runTest {
            val world = InspectionWorld(registered = listOf(aProject(id = "registered", dir = A_PROJECT_DIR)))

            FakeProjectInspector(world)
                .inspect(A_PROJECT_DIR)
                .getOrNull()
                .shouldNotBeNull()
                .alreadyAdded shouldBe ProjectId("registered")
        }

    @Test
    fun `a given ProjectsApi takes precedence over the registered projects of the world`() =
        runTest {
            val world = InspectionWorld(registered = listOf(aProject(id = "registered", dir = A_PROJECT_DIR)))

            FakeProjectInspector(world, projects = FakeProjectsApi())
                .inspect(A_PROJECT_DIR)
                .getOrNull()
                .shouldNotBeNull()
                .alreadyAdded shouldBe null
        }
}
