package dev.agentmanager.projects.api

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

class FakeProjectsApiTest {
    private val storageFailed = ProjectError.StorageFailed(StorageProblem.WRITE_FAILED)
    private val otherDir = Path.of("/Users/test/projects/other")

    @Test
    fun `failNext affects only the next command`() =
        runTest {
            val api = FakeProjectsApi()

            api.failNext(storageFailed)

            api.add(A_PROJECT_DIR) shouldBe Outcome.Err(storageFailed)
            api.add(A_PROJECT_DIR) shouldBe
                Outcome.Ok(Project(ProjectId("project0001"), A_PROJECT_DIR, "agent-manager"))
        }

    @Test
    fun `failNext changes nothing`() =
        runTest {
            val project = aProject()
            val api = FakeProjectsApi(initial = listOf(project))

            api.failNext(storageFailed)
            api.rename(project.id, "Renamed") shouldBe Outcome.Err(storageFailed)
            api.failNext(storageFailed)
            api.remove(project.id) shouldBe Outcome.Err(storageFailed)
            api.failNext(storageFailed)
            api.add(otherDir) shouldBe Outcome.Err(storageFailed)

            api.projects.value shouldContainExactly listOf(project)
        }

    @Test
    fun `calls are recorded in order`() =
        runTest {
            val api = FakeProjectsApi()

            api.add(A_PROJECT_DIR)
            api.rename(ProjectId("project0001"), "Renamed")
            api.remove(ProjectId("project0001"))

            api.calls shouldBe
                listOf(
                    "add $A_PROJECT_DIR",
                    "rename project0001",
                    "remove project0001",
                )
        }

    @Test
    fun `ids are deterministic`() =
        runTest {
            val api = FakeProjectsApi()

            api.add(A_PROJECT_DIR)
            api.add(otherDir)

            api.projects.value.map { it.id } shouldContainExactly
                listOf(ProjectId("project0001"), ProjectId("project0002"))
        }

    @Test
    fun `initial projects are listed`() {
        val first = aProject()
        val second = aProject(id = "other", dir = otherDir, displayName = "Other")

        FakeProjectsApi(initial = listOf(first, second)).projects.value shouldContainExactly listOf(first, second)
    }

    @Test
    fun `default fake accepts paths that do not exist on disk`() =
        runTest {
            val api = FakeProjectsApi()

            api.add(A_PROJECT_DIR, "Agent Manager") shouldBe
                Outcome.Ok(Project(ProjectId("project0001"), A_PROJECT_DIR, "Agent Manager"))
        }

    @Test
    fun `pathKind decides what is at a path`() =
        runTest {
            val api =
                FakeProjectsApi(pathKind = { if (it == A_PROJECT_DIR) PathKind.FILE else PathKind.MISSING })

            api.add(A_PROJECT_DIR) shouldBe Outcome.Err(ProjectError.NotADirectory(A_PROJECT_DIR))
            api.add(otherDir) shouldBe Outcome.Err(ProjectError.NotFound(otherDir))
        }

    @Test
    fun `realPath decides the stored directory`() =
        runTest {
            val link = Path.of("/Users/test/link")
            val api = FakeProjectsApi(realPath = { if (it == link) A_PROJECT_DIR else it })

            val project = api.add(link)

            project shouldBe Outcome.Ok(Project(ProjectId("project0001"), A_PROJECT_DIR, "agent-manager"))
            api.add(A_PROJECT_DIR) shouldBe Outcome.Err(ProjectError.AlreadyAdded(ProjectId("project0001")))
        }

    @Test
    fun `unresolvable path returns NotFound with the normalized path`() =
        runTest {
            FakeProjectsApi(realPath = { null }).add(Path.of("/Users/test/./link")) shouldBe
                Outcome.Err(ProjectError.NotFound(Path.of("/Users/test/link")))
        }

    @Test
    fun `no storage issue by default`() {
        FakeProjectsApi().storageIssue.value shouldBe null
    }

    @Test
    fun `recovered storage is reported and commands work`() =
        runTest {
            val api = FakeProjectsApi(storageIssue = StorageIssue.RECOVERED_FROM_BROKEN_FILE)

            api.storageIssue.value shouldBe StorageIssue.RECOVERED_FROM_BROKEN_FILE
            api.add(A_PROJECT_DIR) shouldBe
                Outcome.Ok(Project(ProjectId("project0001"), A_PROJECT_DIR, "agent-manager"))
        }

    @ParameterizedTest(name = "{0}")
    @CsvSource("READ_FAILED, READ_FAILED", "NEWER_SCHEMA, NEWER_SCHEMA")
    fun `unusable storage fails every command and changes nothing`(
        issue: StorageIssue,
        problem: StorageProblem,
    ) = runTest {
        val project = aProject()
        val api = FakeProjectsApi(initial = listOf(project), storageIssue = issue)
        val failed = Outcome.Err(ProjectError.StorageFailed(problem))

        api.add(otherDir) shouldBe failed
        api.rename(project.id, "Renamed") shouldBe failed
        api.remove(project.id) shouldBe failed

        api.storageIssue.value shouldBe issue
        api.projects.value shouldContainExactly listOf(project)
        api.calls shouldBe listOf("add $otherDir", "rename ${project.id.value}", "remove ${project.id.value}")
    }
}
