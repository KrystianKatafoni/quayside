@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.terminal.ui

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.ShellCommand
import dev.quayside.terminal.api.TerminalError
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Tabs on a fake terminal API: opening, errors, exit and close (FR-T2; AM-503 plan, decisions 6 and 7). */
class TerminalControllerTest {
    @TempDir
    lateinit var root: Path

    private fun projectIn(name: String): TerminalProject = projectIn(root, name)

    @Test
    fun `open shell adds a shown tab in the project dir`() =
        runTest {
            val world = world()
            val alpha = projectIn("Alpha")

            act { world.controller.openShell(alpha) }

            world.state.tabs.size shouldBe 1
            val tab = world.state.tabs.single()
            tab.label shouldBe "zsh"
            tab.kind shouldBe TabKind.SHELL
            tab.project shouldBe alpha
            tab.exitCode shouldBe null
            world.api.shellDirs shouldBe listOf(alpha.dir)
            world.state.layout shouldBe PaneTree.Leaf(world.state.active.shouldNotBeNull(), tab.id)
        }

    @Test
    fun `open command passes the command unchanged`() =
        runTest {
            val world = world()
            val alpha = projectIn("Alpha")
            val command = ShellCommand(listOf("claude", "attach", "abc"), alpha.dir, mapOf("A" to "b"))

            act { world.controller.openCommand(alpha, "attach · abc", command) }

            world.api.fake.commands shouldBe listOf(command)
            val tab = world.state.tabs.single()
            tab.label shouldBe "attach · abc"
            tab.kind shouldBe TabKind.COMMAND
            world.state.layout?.leafOf(tab.id) shouldBe
                world.state.layout
                    ?.leaves()
                    ?.single()
        }

    @Test
    fun `missing dir shows WORK_DIR_MISSING and adds no tab`() =
        runTest {
            val world = world()
            val gone = project("gone", root.resolve("does-not-exist"))

            act { world.controller.openShell(gone) }

            world.state.error shouldBe OpenFailure.WORK_DIR_MISSING
            world.state.tabs.shouldBeEmpty()
            world.state.layout shouldBe null
        }

    @Test
    fun `start failure shows START_FAILED`() =
        runTest {
            val world = world()
            world.api.failWith = TerminalError.StartFailed

            act { world.controller.openShell(projectIn("Alpha")) }

            world.state.error shouldBe OpenFailure.START_FAILED
            world.state.tabs.shouldBeEmpty()
            val warnings = world.logger.entriesAt(LogLevel.WARN)
            warnings.map { it.message } shouldBe listOf("terminal tab open failed")
            warnings.single().fields.containsKey("reason") shouldBe true
        }

    @Test
    fun `dismissing the error clears it`() =
        runTest {
            val world = world()
            world.api.failWith = TerminalError.StartFailed
            act { world.controller.openShell(projectIn("Alpha")) }

            act { world.controller.dismissError() }

            world.state.error shouldBe null
        }

    @Test
    fun `closed api adds nothing and shows no error`() =
        runTest {
            val world = world()
            world.api.close()

            act { world.controller.openShell(projectIn("Alpha")) }

            world.state.tabs.shouldBeEmpty()
            world.state.error shouldBe null
            world.logger.entriesAt(LogLevel.WARN).shouldBeEmpty()
        }

    @Test
    fun `exit code marks the tab and keeps it`() =
        runTest {
            val world = world()
            act { world.controller.openShell(projectIn("Alpha")) }

            act {
                world.api.fake.sessions
                    .single()
                    .finish(3)
            }

            world.state.tabs
                .single()
                .exitCode shouldBe 3
            world.state.layout
                ?.leaves()
                ?.size shouldBe 1
            world.api.fake.sessions
                .single()
                .isClosed shouldBe false
        }

    @Test
    fun `closing a tab closes its session`() =
        runTest {
            val world = world()
            act { world.controller.openShell(projectIn("Alpha")) }

            act { world.controller.closeActive() }

            world.api.fake.sessions
                .single()
                .isClosed shouldBe true
            world.state.tabs.shouldBeEmpty()
            world.state.layout shouldBe null
            world.controller.menu.value.hasTabs shouldBe false
        }

    @Test
    fun `closing by tab id closes that session only`() =
        runTest {
            val world = world()
            val alpha = projectIn("Alpha")
            act { world.controller.openShell(alpha) }
            act { world.controller.openShell(alpha) }
            val first =
                world.state.tabs
                    .first()
                    .id

            act { world.controller.close(first) }

            world.api.fake.sessions
                .map { it.isClosed } shouldBe listOf(true, false)
            world.state.tabs.size shouldBe 1
        }

    @Test
    fun `an exit after the tab was closed changes nothing`() =
        runTest {
            val world = world()
            act { world.controller.openShell(projectIn("Alpha")) }
            val session =
                world.api.fake.sessions
                    .single()
            act { world.controller.closeActive() }

            act { session.finish(1) }

            world.state.tabs.shouldBeEmpty()
        }

    @Test
    fun `logs hold ids and categories only`() =
        runTest {
            val world = world()
            val secret = projectIn("SecretProject")
            val command = ShellCommand(listOf("claude", "attach", "topsecret-session"), secret.dir)
            act { world.controller.openShell(secret) }
            act { world.controller.openCommand(secret, "attach · topsecret-session", command) }
            act { world.controller.split(SplitAxis.SIDE_BY_SIDE) }
            act {
                world.api.fake.sessions
                    .first()
                    .finish(2)
            }
            world.api.failWith = TerminalError.StartFailed
            act { world.controller.openShell(secret) }
            act { world.controller.closeActive() }

            val logged = world.logger.entries.joinToString("\n") { "${it.message} ${it.fields} ${it.cause}" }

            logged shouldNotContain "SecretProject"
            logged shouldNotContain "topsecret"
            logged shouldNotContain root.toString()
            logged shouldNotContain "claude"
        }

    @Test
    fun `shell opens in the dir of the shell start`() =
        runTest {
            val worktree = Files.createDirectory(root.resolve("worktree"))
            val world = world(FixedShellStart(dir = { worktree }))
            val alpha = projectIn("Alpha")

            act { world.controller.openShell(alpha) }

            world.api.shellDirs shouldBe listOf(worktree)
            world.state.tabs
                .single()
                .project.dir shouldBe worktree
        }

    @Test
    fun `shell tab label is read at open time`() =
        runTest {
            val shellStart = FixedShellStart(label = "zsh")
            val world = world(shellStart)
            val alpha = projectIn("Alpha")
            act { world.controller.openShell(alpha) }

            shellStart.label = "fish"
            act { world.controller.openShell(alpha) }

            world.state.tabs.map { it.label } shouldBe listOf("zsh", "fish")
        }

    @Test
    fun `split opens in the dir of the active shell tab`() =
        runTest {
            val worktree = Files.createDirectory(root.resolve("worktree"))
            val world = world(FixedShellStart(dir = { worktree }))
            val alpha = projectIn("Alpha")
            act { world.controller.openShell(alpha) }

            act { world.controller.split(SplitAxis.SIDE_BY_SIDE) }

            world.api.shellDirs shouldBe listOf(worktree, worktree)
            world.state.tabs.size shouldBe 2
        }
}
