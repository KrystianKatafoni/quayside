package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The "Nowy agent" button in the main window (AM-310): the title row of the project page, no longer the top bar. It
 * opens the dialog, shows its shortcut, follows the state of the menu item and never takes the focus.
 */
@OptIn(ExperimentalTestApi::class)
class NewAgentButtonWindowTest {
    @TempDir
    lateinit var root: Path

    private val world by lazy { TopBarWorld(root) }

    /** The button sits on a page, so a project is selected first (without a selection there is no page). */
    private fun ComposeUiTest.openProjectPage() {
        await(WindowTags.projectRow("project0001")).performClick()
        await(WindowTags.NEW_AGENT_BUTTON)
    }

    @Test
    fun `new agent button opens the dialog`() =
        runComposeUiTest {
            with(world) {
                window()
                openProjectPage()

                await(WindowTags.NEW_AGENT_BUTTON).performClick()

                await(WindowTags.NEW_AGENT_DIALOG)
            }
        }

    @Test
    fun `new agent button shows its label and shortcut`() =
        runComposeUiTest {
            with(world) {
                window()
                openProjectPage()

                textIn(WindowTags.NEW_AGENT_BUTTON, "Nowy agent") shouldBe true
                textIn(WindowTags.NEW_AGENT_BUTTON, "⌘N") shouldBe true
            }
        }

    @Test
    fun `no new agent button without projects`() =
        runComposeUiTest {
            with(TopBarWorld(root, projects = emptyList())) {
                window()
                await(WindowTags.MISSION_CONTROL)

                has(WindowTags.NEW_AGENT_BUTTON) shouldBe false
            }
        }

    @Test
    fun `new agent button is on with projects and off while the palette is open`() =
        runComposeUiTest {
            with(world) {
                val palette = window()
                openProjectPage()
                await(WindowTags.NEW_AGENT_BUTTON).assertIsEnabled()

                runOnIdle { palette.open() }

                waitUntil { runCatching { onNodeWithTag(WindowTags.NEW_AGENT_BUTTON).assertIsNotEnabled() }.isSuccess }
            }
        }

    @Test
    fun `new agent button never takes focus`() =
        runComposeUiTest {
            with(world) {
                window()
                openProjectPage()
                onNodeWithTag(WindowTags.KEYS).performSemanticsAction(SemanticsActions.RequestFocus)

                // The button is not focusable: one Tab must not land on it, and Space and Enter stay shortcuts
                press(Key.Tab)

                onNodeWithTag(WindowTags.NEW_AGENT_BUTTON).assert(NOT_FOCUSED)
            }
        }

    @Test
    fun `new agent button is not drawn in terminal mode`() =
        runComposeUiTest {
            with(world) {
                window()
                openProjectPage()

                terminalMode()

                awaitGone(WindowTags.NEW_AGENT_BUTTON)
            }
        }

    private companion object {
        val NOT_FOCUSED =
            SemanticsMatcher("is not focused") { it.config.getOrElse(SemanticsProperties.Focused) { false } != true }
    }
}
