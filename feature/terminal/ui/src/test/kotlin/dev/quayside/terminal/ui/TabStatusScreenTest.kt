package dev.quayside.terminal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
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

    private fun ComposeUiTest.showArea(scale: Float = 1f): TerminalController {
        val built = mutableListOf<TerminalController>()
        setContent {
            AmTestTheme {
                val scope = rememberCoroutineScope()
                val controller =
                    remember {
                        TerminalController(api, scope, RecordingLogger(), FixedShellStart()).also {
                            built +=
                                it
                        }
                    }
                CompositionLocalProvider(LocalDensity provides Density(scale)) {
                    TerminalArea(controller, onAppKey = { false }, paneContent = { Box(Modifier.testTag("content")) })
                }
            }
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
                SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text)),
            WAIT_MS,
        )
    }

    @Test
    fun `keyed tab shows the icon of its status`() =
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
    fun `icon follows a new status`() =
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
    fun `shell tab shows no icon`() =
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
    fun `keyed tab without a status shows no icon`() =
        runComposeUiTest {
            val controller = showArea()
            openKeyed(controller, projectIn("Alpha"))

            runOnIdle { controller.showStatuses(mapOf(TabKey("attach:s2") to TabStatus.FAILED)) }
            waitForIdle()

            onNodeWithTag(TerminalTags.tabStatus(tabId(1))).assertDoesNotExist()
        }

    @Test
    fun `exited keyed tab shows the icon and exit N`() =
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

    private fun ComposeUiTest.iconColors(): Set<Int> {
        val icon = onNodeWithTag(TerminalTags.tabStatus(tabId(1))).captureToImage().toPixelMap()
        return (0 until icon.width).flatMapTo(HashSet()) { x -> (0 until icon.height).map { y -> icon[x, y].toArgb() } }
    }

    @Test
    fun `tab status is a 14 dp icon announced by its state`() =
        runComposeUiTest {
            // Given a keyed tab that waits
            val controller = showArea()
            openKeyed(controller, projectIn("Alpha"))
            runOnIdle { controller.showStatuses(mapOf(key to TabStatus.WAITING)) }
            hasStatusText(1, TabStatus.WAITING)

            // Then the mark is 14 dp square and announced by the text of the status
            onNodeWithTag(TerminalTags.tabStatus(tabId(1)))
                .assertWidthIsEqualTo(14.dp)
                .assertHeightIsEqualTo(14.dp)
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TabStatus::class)
    fun `status icon is drawn in the token colour of its status`(status: TabStatus) =
        runComposeUiTest {
            // Given a keyed tab with that status, in the plex theme, at 4 pixels per dp so strokes are solid
            val tokens = SampleTokens.colors()
            val controller = showArea(scale = 4f)
            openKeyed(controller, projectIn("Alpha"))
            runOnIdle { controller.showStatuses(mapOf(key to status)) }
            hasStatusText(1, status)
            waitForIdle()

            // When the icon is captured, then it has the colour of that status and of no other status tone
            val expected =
                when (status) {
                    TabStatus.WAITING -> tokens.status.warn
                    TabStatus.WORKING -> tokens.status.work
                    TabStatus.DONE -> tokens.status.ok
                    TabStatus.FAILED -> tokens.status.err
                    TabStatus.STOPPED -> tokens.content.muted
                }
            val statusTones = listOf(tokens.status.warn, tokens.status.work, tokens.status.ok, tokens.status.err)
            val drawn = iconColors()
            drawn shouldContain expected.toColor().toArgb()
            (statusTones - expected).forEach { drawn shouldNotContain it.toColor().toArgb() }
        }

    @Test
    fun `tab status icons differ per status`() =
        runComposeUiTest {
            // Given a keyed tab whose status changes through every value
            val controller = showArea(scale = 4f)
            openKeyed(controller, projectIn("Alpha"))
            val masks =
                TabStatus.entries.map { status ->
                    runOnIdle { controller.showStatuses(mapOf(key to status)) }
                    hasStatusText(1, status)
                    waitForIdle()
                    val icon = onNodeWithTag(TerminalTags.tabStatus(tabId(1))).captureToImage().toPixelMap()
                    val background = icon[0, 0]
                    // The shape: where the icon differs from the tab background, whatever its colour
                    (0 until icon.width)
                        .flatMap { x -> (0 until icon.height).map { y -> x to y } }
                        .filter { (x, y) -> icon[x, y] != background }
                        .toSet()
                }

            // Then no two statuses have the same shape (NFR-6: not by colour alone)
            masks.toSet().size shouldBe TabStatus.entries.size
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TabStatus::class)
    fun `every status has a text`(status: TabStatus) {
        TerminalStrings["tab.status.${status.name.lowercase()}"].shouldNotBeBlank()
    }
}
