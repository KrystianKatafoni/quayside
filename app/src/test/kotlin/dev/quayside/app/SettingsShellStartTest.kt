package dev.quayside.app

import dev.quayside.core.domain.ProjectId
import dev.quayside.terminal.api.ShellProfile
import dev.quayside.terminal.api.StartDirectory
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.ui.TerminalProject
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Label and start directory of new shell tabs from the terminal settings (FR-T7, AM-508). */
class SettingsShellStartTest {
    private val projectDir = Path.of("/projects/quayside")
    private val worktree = Path.of("/projects/quayside/.claude/worktrees/fix-login")
    private val home = Path.of("/Users/test")
    private val project = TerminalProject(ProjectId("project0001"), "Quayside", projectDir)

    private val settings = MutableStateFlow(TerminalSettings())

    /** The session selected in Mission Control as the lambda sees it; [sessionOf] is the project it belongs to. */
    private var sessionOf: ProjectId? = null

    private val start =
        SettingsShellStart(settings, Path.of("/bin/zsh"), home) { id -> worktree.takeIf { id == sessionOf } }

    @Test
    fun `session worktree of the selected session of the project`() {
        sessionOf = project.id

        start.dirOf(project) shouldBe worktree
    }

    @Test
    fun `project dir when the selected session is of another project`() {
        sessionOf = ProjectId("project0002")

        start.dirOf(project) shouldBe projectDir
    }

    @Test
    fun `project dir without a selected session`() {
        sessionOf = null

        start.dirOf(project) shouldBe projectDir
    }

    @Test
    fun `home for HOME`() {
        sessionOf = project.id
        settings.value = TerminalSettings(startDirectory = StartDirectory.HOME)

        start.dirOf(project) shouldBe home
    }

    @Test
    fun `project dir for PROJECT even with a selected session`() {
        sessionOf = project.id
        settings.value = TerminalSettings(startDirectory = StartDirectory.PROJECT)

        start.dirOf(project) shouldBe projectDir
    }

    @Test
    fun `label follows the profile`() {
        start.label shouldBe "zsh"

        settings.value = TerminalSettings(shell = ShellProfile.FISH)
        start.label shouldBe "fish"

        settings.value = TerminalSettings(shell = ShellProfile.BASH)
        start.label shouldBe "bash"
    }

    @Test
    fun `settings and selection are read at every call`() {
        start.dirOf(project) shouldBe projectDir

        sessionOf = project.id
        start.dirOf(project) shouldBe worktree

        settings.value = TerminalSettings(startDirectory = StartDirectory.HOME)
        start.dirOf(project) shouldBe home
    }
}
