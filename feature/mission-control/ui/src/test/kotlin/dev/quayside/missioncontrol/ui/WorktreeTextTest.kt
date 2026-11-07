package dev.quayside.missioncontrol.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** The worktree line of the details (AM-816): relative to the project directory when under it. */
internal class WorktreeTextTest {
    private val project = Path.of("/Users/test/projects/agent-manager")

    @Test
    fun `worktree is relative under the project`() {
        worktreeText(project.resolve(".claude/worktrees/fix-login"), project) shouldBe ".claude/worktrees/fix-login"
        worktreeText(project.resolve("app"), project) shouldBe "app"
    }

    @Test
    fun `worktree outside the project is absolute`() {
        val elsewhere = Path.of("/Users/test/elsewhere/tree")

        worktreeText(elsewhere, project) shouldBe "/Users/test/elsewhere/tree"
    }

    @Test
    fun `worktree next to the project with the same name prefix is absolute`() {
        // a path prefix as text is not a parent directory
        val sibling = Path.of("/Users/test/projects/agent-manager-2/tree")

        worktreeText(sibling, project) shouldBe "/Users/test/projects/agent-manager-2/tree"
    }

    @Test
    fun `worktree equal to the project is dot`() {
        worktreeText(project, project) shouldBe "."
    }

    @Test
    fun `worktree of the parent directory is absolute`() {
        worktreeText(project.parent, project) shouldBe "/Users/test/projects"
    }
}
