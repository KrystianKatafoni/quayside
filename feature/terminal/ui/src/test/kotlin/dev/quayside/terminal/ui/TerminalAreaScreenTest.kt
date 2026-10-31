package dev.quayside.terminal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Cold JVM start-up (AWT, first Swing panel, first Compose frame) can exceed the default 1 s of `waitUntil`
 * in the first test of a class on a slow CI runner; the waits use this bound instead.
 */
private const val WAIT_MS = 5_000L

/** User scenarios of the tab bar and the panes on a fake terminal API; widgets are replaced by markers (FR-T2). */
@OptIn(ExperimentalTestApi::class)
class TerminalAreaScreenTest {
    @TempDir
    lateinit var root: Path

    private val api = ScriptedTerminalApi()

    private fun projectIn(name: String): TerminalProject =
        project(name.lowercase(), Files.createDirectory(root.resolve(name)), name)

    private fun contentTag(session: String) = "content.$session"

    /** Shows the area; each pane draws a marker tagged with the id of the session it shows. */
    private fun ComposeUiTest.showArea(): TerminalController {
        val built = mutableListOf<TerminalController>()
        setContent {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    TerminalController(api, scope, RecordingLogger(), FixedShellStart()).also {
                        built +=
                            it
                    }
                }
            TerminalArea(controller, onAppKey = { false }, paneContent = { component ->
                val id =
                    api.fake.sessions
                        .first { it.component() === component }
                        .id.value
                Box(Modifier.testTag(contentTag(id)))
            })
        }
        waitUntil(timeoutMillis = WAIT_MS) { built.isNotEmpty() }
        return built.single()
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag), WAIT_MS)
        return onNodeWithTag(tag)
    }

    private fun ComposeUiTest.openShell(
        controller: TerminalController,
        project: TerminalProject,
    ) {
        val before = api.fake.sessions.size
        runOnIdle { controller.openShell(project) }
        waitUntil(timeoutMillis = WAIT_MS) {
            api.fake.sessions.size > before &&
                controller.state.value.tabs.size > before
        }
    }

    private fun ComposeUiTest.splitTo(
        controller: TerminalController,
        panes: Int,
        axis: SplitAxis = SplitAxis.SIDE_BY_SIDE,
    ) {
        while ((
                controller.state.value.layout
                    ?.leaves()
                    ?.size ?: 0
            ) < panes
        ) {
            val before = api.fake.sessions.size
            runOnIdle { controller.split(axis) }
            waitUntil(timeoutMillis = WAIT_MS) {
                api.fake.sessions.size > before &&
                    controller.state.value.tabs.size > before
            }
        }
    }

    private fun ComposeUiTest.hasContent(session: Int): Boolean =
        onAllNodes(hasTestTag(contentTag("fake-$session"))).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `tab bar shows project names and tab labels`() =
        runComposeUiTest {
            // Given shells in two projects and an attach tab in the first
            val alpha = projectIn("Alpha")
            val beta = projectIn("Beta")
            val controller = showArea()
            openShell(controller, alpha)
            openShell(controller, beta)
            runOnIdle {
                controller.openCommand(
                    alpha,
                    "attach · fix-login",
                    ShellCommand(listOf("claude", "attach", "x"), alpha.dir),
                )
            }
            waitUntil { controller.state.value.tabs.size == 3 }

            // Then the bar has one group per project and a chip per tab
            await(TerminalTags.group(alpha.id)).assertTextContains("Alpha", substring = true)
            await(TerminalTags.group(beta.id)).assertTextContains("Beta", substring = true)
            val tabs = controller.state.value.tabs
            await(TerminalTags.tab(tabs[0].id)).assertTextContains("zsh", substring = true)
            await(TerminalTags.tab(tabs[2].id)).assertTextContains("attach · fix-login", substring = true)
        }

    @Test
    fun `clicking a tab shows it in the active pane`() =
        runComposeUiTest {
            // Given two shells of one project; the second is shown
            val controller = showArea()
            val alpha = projectIn("Alpha")
            openShell(controller, alpha)
            openShell(controller, alpha)
            await(contentTag("fake-2"))
            hasContent(1) shouldBe false

            // When the user clicks the first tab
            await(TerminalTags.tab(tabId(1))).performClick()

            // Then the first shell is shown instead
            await(contentTag("fake-1"))
            waitUntilDoesNotExist(hasTestTag(contentTag("fake-2")))
        }

    @Test
    fun `close button closes the tab`() =
        runComposeUiTest {
            // Given one open tab
            val controller = showArea()
            openShell(controller, projectIn("Alpha"))
            await(TerminalTags.tab(tabId(1)))

            // When the user clicks its x
            await(TerminalTags.tabClose(tabId(1))).performClick()

            // Then the tab, its pane and the session are gone
            waitUntilDoesNotExist(hasTestTag(TerminalTags.tab(tabId(1))))
            api.fake.sessions
                .single()
                .isClosed shouldBe true
            hasContent(1) shouldBe false
        }

    @Test
    fun `exited tab shows its exit code`() =
        runComposeUiTest {
            // Given a running shell
            val controller = showArea()
            openShell(controller, projectIn("Alpha"))
            onNodeWithTag(TerminalTags.tabExit(tabId(1))).assertDoesNotExist()

            // When the process exits with 3
            runOnIdle {
                api.fake.sessions
                    .single()
                    .finish(3)
            }

            // Then the tab stays with the marker "exit 3"
            await(TerminalTags.tabExit(tabId(1))).assertTextContains("exit 3", substring = true)
            await(TerminalTags.tab(tabId(1)))
            await(contentTag("fake-1"))
        }

    @Test
    fun `error bar shows and dismisses`() =
        runComposeUiTest {
            // Given a project whose directory is gone
            val controller = showArea()
            onNodeWithTag(TerminalTags.ERROR).assertDoesNotExist()

            // When opening a shell there fails
            runOnIdle { controller.openShell(project("gone", root.resolve("missing"))) }

            // Then the error bar shows and the x dismisses it
            await(TerminalTags.ERROR)
            await(TerminalTags.ERROR_DISMISS).performClick()
            waitUntilDoesNotExist(hasTestTag(TerminalTags.ERROR))
        }

    @Test
    fun `two panes are shown side by side`() =
        runComposeUiTest {
            // Given a shell split side by side
            val controller = showArea()
            openShell(controller, projectIn("Alpha"))
            splitTo(controller, panes = 2)

            // Then both panes show their shells next to each other
            val first = await(TerminalTags.pane(pane(1))).fetchSemanticsNode().boundsInRoot
            val second = await(TerminalTags.pane(pane(2))).fetchSemanticsNode().boundsInRoot
            (first.right <= second.left) shouldBe true
            first.top shouldBe second.top
            await(contentTag("fake-1"))
            await(contentTag("fake-2"))
        }

    @Test
    fun `stacked split puts the new pane under the first`() =
        runComposeUiTest {
            val controller = showArea()
            openShell(controller, projectIn("Alpha"))
            splitTo(controller, panes = 2, axis = SplitAxis.STACKED)

            val first = await(TerminalTags.pane(pane(1))).fetchSemanticsNode().boundsInRoot
            val second = await(TerminalTags.pane(pane(2))).fetchSemanticsNode().boundsInRoot
            (first.bottom <= second.top) shouldBe true
            first.left shouldBe second.left
        }

    @Test
    fun `active pane has the accent frame`() =
        runComposeUiTest {
            // Given two panes; the new one is active
            val controller = showArea()
            openShell(controller, projectIn("Alpha"))
            splitTo(controller, panes = 2)
            await(TerminalTags.pane(pane(2))).assertIsSelected()
            await(TerminalTags.pane(pane(1))).assertIsNotSelected()

            // When the user moves to the previous pane
            runOnIdle { controller.focusPrevious() }

            // Then the frame follows
            waitUntil { controller.state.value.active == pane(1) }
            await(TerminalTags.pane(pane(1))).assertIsSelected()
            await(TerminalTags.pane(pane(2))).assertIsNotSelected()
        }

    @Test
    fun `maximized layout shows only the active pane`() =
        runComposeUiTest {
            // Given two panes, the second active
            val controller = showArea()
            openShell(controller, projectIn("Alpha"))
            splitTo(controller, panes = 2)

            // When the active pane is maximized
            runOnIdle { controller.toggleMaximized() }

            // Then only it is shown; restoring brings the other back
            waitUntilDoesNotExist(hasTestTag(TerminalTags.pane(pane(1))))
            await(TerminalTags.pane(pane(2)))
            hasContent(1) shouldBe false
            hasContent(2) shouldBe true
            runOnIdle { controller.toggleMaximized() }
            await(TerminalTags.pane(pane(1)))
            await(TerminalTags.pane(pane(2)))
        }
}
