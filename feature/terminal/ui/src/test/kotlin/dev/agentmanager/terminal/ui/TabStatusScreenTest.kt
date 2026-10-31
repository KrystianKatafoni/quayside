package dev.agentmanager.terminal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.testing.RecordingLogger
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files
import java.nio.file.Path

private const val WAIT_MS = 5_000L

/** The state dot next to a keyed tab, on a fake terminal API (FR-T2; plan AM-510, decision 6). */
@OptIn(ExperimentalTestApi::class)
class TabStatusScreenTest {
    @TempDir
    lateinit var root: Path

    private val api = ScriptedTerminalApi()
    private val key = TabKey("attach:s1")

    private fun projectIn(name: String): TerminalProject =
        project(name.lowercase(), Files.createDirectory(root.resolve(name)), name)

    private fun ComposeUiTest.showArea(): TerminalController {
        val built = mutableListOf<TerminalController>()
        setContent {
            val scope = rememberCoroutineScope()
            val controller = remember { TerminalController(api, scope, RecordingLogger(), "zsh").also { built += it } }
            TerminalArea(controller, onAppKey = { false }, paneContent = { Box(Modifier.testTag("content")) })
        }
        waitUntil(timeoutMillis = WAIT_MS) { built.isNotEmpty() }
        return built.single()
    }

    private fun ComposeUiTest.openKeyed(
        controller: TerminalController,
        project: TerminalProject,
    ) {
        runOnIdle { controller.focusOrOpen(project, key, "attach · s1", ShellCommand(listOf("claude"), project.dir)) }
        waitUntil(timeoutMillis = WAIT_MS) {
            controller.state.value.tabs
                .isNotEmpty()
        }
        waitUntilExactlyOneExists(hasTestTag(TerminalTags.tab(tabId(1))), WAIT_MS)
    }

    private fun ComposeUiTest.hasStatusText(
        n: Int,
        status: TabStatus,
    ) {
        val text = TerminalStrings["tab.status.${status.name.lowercase()}"]
        waitUntilExactlyOneExists(hasTestTag(TerminalTags.tabStatus(tabId(n))), WAIT_MS)
        waitUntilExactlyOneExists(
            hasTestTag(TerminalTags.tabStatus(tabId(n))) and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text),
            WAIT_MS,
        )
    }

    @Test
    fun `keyed tab shows the dot of its status`() =
        runComposeUiTest {
            // Given a keyed tab
            val controller = showArea()
            openKeyed(controller, projectIn("Alpha"))
            onNodeWithTag(TerminalTags.tabStatus(tabId(1))).assertDoesNotExist()

            // When its status is WAITING
            runOnIdle { controller.showStatuses(mapOf(key to TabStatus.WAITING)) }

            // Then the dot shows with the text of that status
            hasStatusText(1, TabStatus.WAITING)
        }

    @Test
    fun `dot follows a new status`() =
        runComposeUiTest {
            val controller = showArea()
            openKeyed(controller, projectIn("Alpha"))
            runOnIdle { controller.showStatuses(mapOf(key to TabStatus.WORKING)) }
            hasStatusText(1, TabStatus.WORKING)

            runOnIdle { controller.showStatuses(mapOf(key to TabStatus.DONE)) }

            hasStatusText(1, TabStatus.DONE)

            runOnIdle { controller.showStatuses(emptyMap()) }

            waitUntilDoesNotExist(hasTestTag(TerminalTags.tabStatus(tabId(1))), WAIT_MS)
        }

    @Test
    fun `shell tab shows no dot`() =
        runComposeUiTest {
            // Given a shell tab and statuses for a key no tab has
            val controller = showArea()
            val alpha = projectIn("Alpha")
            runOnIdle { controller.openShell(alpha) }
            waitUntilExactlyOneExists(hasTestTag(TerminalTags.tab(tabId(1))), WAIT_MS)

            // When a status arrives
            runOnIdle { controller.showStatuses(mapOf(key to TabStatus.WORKING)) }
            waitForIdle()

            // Then the shell tab has no dot
            onNodeWithTag(TerminalTags.tabStatus(tabId(1))).assertDoesNotExist()
        }

    @Test
    fun `keyed tab without a status shows no dot`() =
        runComposeUiTest {
            val controller = showArea()
            openKeyed(controller, projectIn("Alpha"))

            runOnIdle { controller.showStatuses(mapOf(TabKey("attach:s2") to TabStatus.FAILED)) }
            waitForIdle()

            onNodeWithTag(TerminalTags.tabStatus(tabId(1))).assertDoesNotExist()
        }

    @Test
    fun `exited keyed tab shows the dot and exit N`() =
        runComposeUiTest {
            val controller = showArea()
            openKeyed(controller, projectIn("Alpha"))
            runOnIdle { controller.showStatuses(mapOf(key to TabStatus.STOPPED)) }

            runOnIdle {
                api.fake.sessions
                    .single()
                    .finish(3)
            }

            waitUntilExactlyOneExists(hasTestTag(TerminalTags.tabExit(tabId(1))), WAIT_MS)
            onNodeWithTag(TerminalTags.tabExit(tabId(1))).assertTextContains("exit 3", substring = true)
            hasStatusText(1, TabStatus.STOPPED)
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TabStatus::class)
    fun `every status has a text`(status: TabStatus) {
        TerminalStrings["tab.status.${status.name.lowercase()}"].shouldNotBeBlank()
    }
}
