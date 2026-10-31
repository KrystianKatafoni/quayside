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
import java.nio.file.Path

/** Panes of the controller: split, focus, menu state (FR-T2; AM-503 plan, decisions 3 to 5). */
class TerminalPanesControllerTest {
    @TempDir
    lateinit var root: Path

    private fun projectIn(name: String): TerminalProject = projectIn(root, name)

    @Test
    fun `split opens a shell in the active tab's project dir`() =
        runTest {
            val world = world()
            val alpha = projectIn("Alpha")
            val beta = projectIn("Beta")
            act { world.controller.openShell(beta) }
            act { world.controller.openShell(alpha) }

            act { world.controller.split(SplitAxis.STACKED) }

            world.api.shellDirs shouldBe listOf(beta.dir, alpha.dir, alpha.dir)
            val layout = world.state.layout as PaneTree.Split
            layout.axis shouldBe SplitAxis.STACKED
            world.state.tabs
                .last()
                .project shouldBe alpha
            world.state.active shouldBe
                world.state.layout
                    ?.leaves()
                    ?.last()
                    ?.pane
        }

    @Test
    fun `split is ignored without tabs`() =
        runTest {
            val world = world()

            act { world.controller.split(SplitAxis.SIDE_BY_SIDE) }

            world.api.fake.sessions
                .shouldBeEmpty()
            world.state.tabs.shouldBeEmpty()
        }

    @Test
    fun `split is ignored with four panes`() =
        runTest {
            val world = world()
            act { world.controller.openShell(projectIn("Alpha")) }
            repeat(3) { act { world.controller.split(SplitAxis.SIDE_BY_SIDE) } }
            world.state.layout
                ?.leaves()
                ?.size shouldBe 4
            world.controller.menu.value.canSplit shouldBe false

            act { world.controller.split(SplitAxis.SIDE_BY_SIDE) }

            world.api.fake.sessions.size shouldBe 4
            world.state.layout
                ?.leaves()
                ?.size shouldBe 4
        }

    @Test
    fun `second split while one is pending is ignored`() =
        runTest {
            val world = world()
            act { world.controller.openShell(projectIn("Alpha")) }
            val gate = CompletableDeferred<Unit>()
            world.api.gate = gate

            act { world.controller.split(SplitAxis.SIDE_BY_SIDE) }
            act { world.controller.split(SplitAxis.STACKED) }
            world.controller.menu.value.canSplit shouldBe false
            gate.complete(Unit)
            runCurrent()

            world.api.fake.sessions.size shouldBe 2
            world.state.layout
                ?.leaves()
                ?.size shouldBe 2
            world.controller.menu.value.canSplit shouldBe true
        }

    @Test
    fun `failed split keeps the layout`() =
        runTest {
            val world = world()
            act { world.controller.openShell(projectIn("Alpha")) }
            val before = world.state.layout
            world.api.failWith = TerminalError.StartFailed

            act { world.controller.split(SplitAxis.SIDE_BY_SIDE) }

            world.state.layout shouldBe before
            world.state.tabs.size shouldBe 1
            world.state.error shouldBe OpenFailure.START_FAILED
            world.controller.menu.value.canSplit shouldBe true
        }

    @Test
    fun `menu state follows tabs, panes and maximize`() =
        runTest {
            val world = world()
            val menu = world.controller.menu
            menu.value shouldBe
                TerminalMenuState(hasTabs = false, canSplit = false, canSwitchPane = false, maximized = false)

            act { world.controller.openShell(projectIn("Alpha")) }
            menu.value shouldBe
                TerminalMenuState(hasTabs = true, canSplit = true, canSwitchPane = false, maximized = false)

            act { world.controller.split(SplitAxis.SIDE_BY_SIDE) }
            menu.value shouldBe
                TerminalMenuState(hasTabs = true, canSplit = true, canSwitchPane = true, maximized = false)

            act { world.controller.toggleMaximized() }
            menu.value.maximized shouldBe true

            act { world.controller.toggleMaximized() }
            menu.value.maximized shouldBe false
        }

    @Test
    fun `next and previous pane move the active pane`() =
        runTest {
            val world = world()
            act { world.controller.openShell(projectIn("Alpha")) }
            act { world.controller.split(SplitAxis.SIDE_BY_SIDE) }
            val panes =
                world.state.layout
                    .shouldNotBeNull()
                    .leaves()
                    .map { it.pane }
            world.state.active shouldBe panes[1]

            act { world.controller.focusNext() }
            world.state.active shouldBe panes[0]

            act { world.controller.focusPrevious() }
            world.state.active shouldBe panes[1]

            act { world.controller.focusPane(panes[0]) }
            world.state.active shouldBe panes[0]
        }

    @Test
    fun `selecting a tab shows it`() =
        runTest {
            val world = world()
            val alpha = projectIn("Alpha")
            act { world.controller.openShell(alpha) }
            act { world.controller.openShell(alpha) }
            val first =
                world.state.tabs
                    .first()
                    .id
            world.state.layout?.leafOf(first) shouldBe null

            act { world.controller.select(first) }

            world.state.layout?.leafOf(first) shouldBe
                world.state.layout
                    ?.leaves()
                    ?.single()
        }
}
