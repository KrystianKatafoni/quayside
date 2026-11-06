package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.quayside.notifications.api.NotificationSettings
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The top bar of the window on fakes (AM-816, decisions 1 and 3): crumbs, the search field, the voice chip, the bell
 * and "Nowy agent", above both layouts. Menus do not work in `runComposeUiTest`, so the controls are clicked.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarWindowTest {
    @TempDir
    lateinit var root: Path

    private val world by lazy { TopBarWorld(root) }

    @Test
    fun `top bar new agent opens the dialog`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.projectRow("project0001")).performClick()

                await(WindowTags.NEW_AGENT_BUTTON).performClick()

                await(WindowTags.NEW_AGENT_DIALOG)
            }
        }

    @Test
    fun `top bar new agent shows its shortcut`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.NEW_AGENT_BUTTON)

                textIn(WindowTags.NEW_AGENT_BUTTON, "Nowy agent") shouldBe true
                textIn(WindowTags.NEW_AGENT_BUTTON, "⌘N") shouldBe true
            }
        }

    @Test
    fun `top bar new agent is off without projects`() =
        runComposeUiTest {
            with(TopBarWorld(root, projects = emptyList())) {
                window()

                await(WindowTags.NEW_AGENT_BUTTON).assertIsNotEnabled()
            }
        }

    @Test
    fun `top bar new agent is on with projects and off while the palette is open`() =
        runComposeUiTest {
            with(world) {
                val palette = window()
                await(WindowTags.NEW_AGENT_BUTTON).assertIsEnabled()

                runOnIdle { palette.open() }

                waitUntil { runCatching { onNodeWithTag(WindowTags.NEW_AGENT_BUTTON).assertIsNotEnabled() }.isSuccess }
            }
        }

    @Test
    fun `top bar new agent never takes focus`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.NEW_AGENT_BUTTON)
                onNodeWithTag(WindowTags.KEYS).performSemanticsAction(SemanticsActions.RequestFocus)

                // The button is the only control of the top bar that could take the focus: one Tab would land on it
                press(Key.Tab)

                onNodeWithTag(WindowTags.NEW_AGENT_BUTTON).assert(NOT_FOCUSED)
            }
        }

    @Test
    fun `search field opens the palette`() =
        runComposeUiTest {
            with(world) {
                window()
                textIn(WindowTags.SEARCH, "Szukaj sesji lub wpisz komendę…") shouldBe true
                textIn(WindowTags.SEARCH, "⌘K") shouldBe true

                await(WindowTags.SEARCH).performClick()

                await(WindowTags.PALETTE_FIELD)
            }
        }

    @Test
    fun `voice chip opens notification settings`() =
        runComposeUiTest {
            with(world) {
                window()

                await(WindowTags.VOICE_CHIP).performClick()

                await(WindowTags.NOTIFICATION_SETTINGS_DIALOG)
            }
        }

    @Test
    fun `voice chip follows the settings`() =
        runComposeUiTest {
            with(TopBarWorld(root, notifications = NotificationSettings(voice = false))) {
                window()

                await(WindowTags.VOICE_CHIP)
                textIn(WindowTags.VOICE_CHIP, "Głos wył.") shouldBe true
            }
        }

    @Test
    fun `bell counts the waiting sessions of every project`() =
        runComposeUiTest {
            with(world) {
                window()

                // Given a waiting session in each project, the bell announces 2; the working one is not counted
                await(WindowTags.BELL)
                waitUntil { announcedNumbers() == listOf("2") }
            }
        }

    @Test
    fun `bell click shows the waiting view and clears the project`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.projectRow("project0001")).performClick()
                await(WindowTags.projectRow("project0001")).assertIsSelected()

                await(WindowTags.BELL).performClick()

                await(WindowTags.view("waiting")).assertIsSelected()
                await(WindowTags.missionRow("their-ask"))
                await(WindowTags.projectRow("project0001")).assertIsNotSelected()
            }
        }

    @Test
    fun `bell in terminal mode returns to the list with the waiting view`() =
        runComposeUiTest {
            with(world) {
                window()
                terminalMode()
                has(WindowTags.RAIL) shouldBe true

                await(WindowTags.BELL).performClick()

                // The list layout is back: the sidebar and Mission Control, no rail; the waiting view is shown
                await(WindowTags.SIDEBAR)
                await(WindowTags.MISSION_CONTROL)
                awaitGone(WindowTags.RAIL)
                await(WindowTags.view("waiting")).assertIsSelected()
                await(WindowTags.missionRow("their-ask"))
            }
        }

    @Test
    fun `space after clicking the bell reaches the window`() =
        runComposeUiTest {
            with(world) {
                window()
                await(WindowTags.BELL).performClick()
                await(WindowTags.missionRow("their-ask"))

                // When the user selects a session and presses space, the preview opens: the bell did not eat the key
                press(Key.J)
                press(Key.Spacebar)

                await(WindowTags.PREVIEW)
                onNodeWithTag(WindowTags.BELL).assertIsNotFocused()
            }
        }

    @Test
    fun `breadcrumbs follow project and global view`() =
        runComposeUiTest {
            with(world) {
                window()
                awaitText(WindowTags.CRUMBS, "Quayside")
                textIn(WindowTags.CRUMBS, "Harbor") shouldBe false
                textIn(WindowTags.CRUMBS, "Other") shouldBe false

                await(WindowTags.projectRow("project0001")).performClick()
                awaitText(WindowTags.CRUMBS, "Harbor")

                await(WindowTags.projectRow("project0002")).performClick()
                awaitText(WindowTags.CRUMBS, "Other")
                textIn(WindowTags.CRUMBS, "Harbor") shouldBe false

                await(WindowTags.view("waiting")).performClick()
                awaitText(WindowTags.CRUMBS, "Czeka na mnie")
                textIn(WindowTags.CRUMBS, "Other") shouldBe false
                textIn(WindowTags.CRUMBS, "Quayside") shouldBe true
            }
        }

    @Test
    fun `breadcrumbs in terminal mode end with the session name`() =
        runComposeUiTest {
            with(world) {
                window()
                terminalMode()

                awaitText(WindowTags.CRUMBS, "fix-login")
                textIn(WindowTags.CRUMBS, "Harbor") shouldBe true
            }
        }

    @Test
    fun `session crumb of terminal mode uses the name role`() =
        runComposeUiTest {
            with(world) {
                window()
                terminalMode()
                awaitText(WindowTags.CRUMBS, "fix-login")

                val session = crumbStyle("fix-login")
                val project = crumbStyle("Harbor")

                session.fontSize shouldBe 15.sp
                session.fontWeight shouldBe FontWeight.SemiBold
                project.fontSize shouldBe 13.sp
                project.fontWeight shouldBe FontWeight.Normal
            }
        }

    @Test
    fun `back to the list with the tab still open drops the session crumb`() =
        runComposeUiTest {
            with(world) {
                window()
                terminalMode()
                awaitText(WindowTags.CRUMBS, "fix-login")

                await(ModeTags.BACK).performClick()
                await(WindowTags.SIDEBAR)
                awaitGone(WindowTags.RAIL)

                // the attach tab is still there, but the list layout shows no session crumb
                has(WindowTags.terminalTab(1)) shouldBe true
                waitUntil { !textIn(WindowTags.CRUMBS, "fix-login") }
                textIn(WindowTags.CRUMBS, "Harbor") shouldBe true
            }
        }

    private fun ComposeUiTest.crumbStyle(text: String): TextStyle {
        val node =
            onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(WindowTags.CRUMBS)), useUnmergedTree = true)
                .onFirst()
                .fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        checkNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)).action?.invoke(results)
        return results.first().layoutInput.style
    }

    @Test
    fun `top bar sits above the sidebar and Mission Control`() =
        runComposeUiTest {
            with(world) {
                window()

                val bar = await(WindowTags.TOP_BAR).fetchSemanticsNode().boundsInRoot
                val sidebar = await(WindowTags.SIDEBAR).fetchSemanticsNode().boundsInRoot
                val mission = await(WindowTags.MISSION_CONTROL).fetchSemanticsNode().boundsInRoot

                bar.bottom shouldBeLessThanOrEqualTo sidebar.top
                bar.bottom shouldBeLessThanOrEqualTo mission.top
            }
        }

    @Test
    fun `top bar sits above the rail in terminal mode and ends with new agent`() =
        runComposeUiTest {
            with(world) {
                window()
                terminalMode()

                val bar = await(WindowTags.TOP_BAR).fetchSemanticsNode().boundsInRoot
                val rail = await(WindowTags.RAIL).fetchSemanticsNode().boundsInRoot
                val newAgent = await(WindowTags.NEW_AGENT_BUTTON).fetchSemanticsNode().boundsInRoot

                bar.bottom shouldBeLessThanOrEqualTo rail.top
                // "Nowy agent" is the last control at the right edge of the bar
                (bar.right - newAgent.right) shouldBeLessThanOrEqualTo 24f
            }
        }

    /** Every number that a screen reader finds in the bell: texts and descriptions of its whole subtree. */
    private fun ComposeUiTest.announcedNumbers(): List<String> =
        onAllNodes(hasTestTag(WindowTags.BELL) or hasAnyAncestor(hasTestTag(WindowTags.BELL)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node ->
                val texts = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
                texts + node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            }.flatMap { Regex("\\d+").findAll(it).map { match -> match.value }.toList() }
            .distinct()

    private companion object {
        val NOT_FOCUSED =
            SemanticsMatcher("is not focused") { it.config.getOrElse(SemanticsProperties.Focused) { false } != true }
    }
}
