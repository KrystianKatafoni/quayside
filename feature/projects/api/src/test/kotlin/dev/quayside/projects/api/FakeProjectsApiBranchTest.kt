package dev.quayside.projects.api

import dev.quayside.core.domain.ProjectId
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The branch a test sets on [FakeProjectsApi] (AM-818). */
class FakeProjectsApiBranchTest {
    private val project = aProject(id = "p1")

    @Test
    fun `a set branch is reported for a registered project`() =
        runTest {
            val api = FakeProjectsApi(listOf(project))

            api.setBranch(project.id, "main")

            api.branch(project.id) shouldBe "main"
        }

    @Test
    fun `no branch is set by default and null clears it`() =
        runTest {
            val api = FakeProjectsApi(listOf(project))
            api.branch(project.id) shouldBe null

            api.setBranch(project.id, "main")
            api.setBranch(project.id, null)

            api.branch(project.id) shouldBe null
        }

    @Test
    fun `a branch of a project that is not registered is not reported`() =
        runTest {
            val api = FakeProjectsApi(listOf(project))

            api.setBranch(ProjectId("other"), "main")

            api.branch(ProjectId("other")) shouldBe null
        }

    @Test
    fun `a removed project has no branch`() =
        runTest {
            val api = FakeProjectsApi(listOf(project))
            api.setBranch(project.id, "main")

            api.remove(project.id)

            api.branch(project.id) shouldBe null
        }
}
