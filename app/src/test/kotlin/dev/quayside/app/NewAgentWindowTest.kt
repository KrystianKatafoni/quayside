package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.SessionPermissionMode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Starting an agent from the window on fakes (FR-S3, FR-K1, FR-K2, FR-K3; AM-608, docs/12 §4 scenario 3): the three
 * ways in (button, ⌘N, palette), the options that reach `start`, and the selection after it. Nothing starts a real
 * `claude`: the sessions are `FakeSessions`.
 */
@OptIn(ExperimentalTestApi::class)
class NewAgentWindowTest {
    private val world = AgentWorld()

    @Test
    fun `FR-S3 header button opens the dialog and starts a working agent`() =
        runComposeUiTest {
            // Given a selected project and no session
            showWindowOf(world.setup)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.DETAILS_EMPTY)

            // When the user clicks "Nowy agent", types a prompt and clicks "Uruchom"
            await(WindowTags.NEW_AGENT_BUTTON).performClick()
            await(WindowTags.NEW_AGENT_DIALOG)
            awaitText(WindowTags.NEW_AGENT_PROJECT, "Quayside")
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).performTextInput("Fix the login")
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()

            // Then the dialog is gone, one agent started in that project, and its working row is selected
            awaitGone(WindowTags.NEW_AGENT_DIALOG)
            world.fake.calls shouldBe listOf("start project0001")
            await(WindowTags.missionRow("00000001"))
            awaitDetails("00000001")
            awaitDetails("Pracuje")
            waitUntilExactlyOneExists(hasText("Quayside") and hasAnyAncestor(hasTestTag(WindowTags.HEADER)))
        }

    @Test
    fun `FR-K2 cmd n starts an agent in the selected project`() =
        runComposeUiTest {
            // Given the second project selected in the sidebar
            showWindowOf(world.setup)
            await(WindowTags.projectRow("project0002")).performClick()
            await(WindowTags.DETAILS_EMPTY)

            // When the user presses cmd N, types a prompt and presses cmd Enter
            pressCmdN(world.setup)
            await(WindowTags.NEW_AGENT_DIALOG)
            awaitText(WindowTags.NEW_AGENT_PROJECT, "Other")
            awaitFocus(WindowTags.NEW_AGENT_PROMPT)
            typePrompt("Fix the login")
            onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).pressWithMeta(Key.Enter)

            // Then the agent started in the selected project and the dialog is gone
            awaitGone(WindowTags.NEW_AGENT_DIALOG)
            world.fake.calls shouldBe listOf("start project0002")
            awaitDetails("00000001")
        }

    @Test
    fun `FR-K1 palette command opens the dialog`() =
        runComposeUiTest {
            // Given the window with a selected project and a closed palette
            val palette = showWindowOf(world.setup)
            await(WindowTags.projectRow("project0001")).performClick()
            await(WindowTags.DETAILS_EMPTY)

            // When the user picks "Nowy agent…" in the palette
            openFromPalette(palette)

            // Then the dialog is open on the selected project, with the focus on the prompt
            await(WindowTags.NEW_AGENT_DIALOG)
            awaitText(WindowTags.NEW_AGENT_PROJECT, "Quayside")
            awaitFocus(WindowTags.NEW_AGENT_PROMPT)
            world.fake.calls.shouldBeEmpty()
        }

    @Test
    fun `FR-S3 options reach the start`() =
        runComposeUiTest {
            // Given the open dialog on the first project
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")

            // When the user fills every field and picks a model and a mode
            typePrompt("Fix the login")
            onNodeWithTag(WindowTags.NEW_AGENT_NAME).performTextInput("login-fix")
            onNodeWithTag(WindowTags.NEW_AGENT_AGENT).performTextInput("code-reviewer")
            repeat(2) { onNodeWithTag(WindowTags.NEW_AGENT_MODEL).performClick() }
            repeat(2) { onNodeWithTag(WindowTags.NEW_AGENT_PERMISSION).performClick() }
            awaitText(WindowTags.NEW_AGENT_MODEL, "opus")
            awaitText(WindowTags.NEW_AGENT_PERMISSION, "Tylko plan")
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()

            // Then they all reach the start, and the session carries the name
            awaitGone(WindowTags.NEW_AGENT_DIALOG)
            world.starts.specs shouldBe
                listOf(
                    world.quayside.id to
                        NewSessionSpec(
                            "Fix the login",
                            "login-fix",
                            "code-reviewer",
                            "opus",
                            SessionPermissionMode.PLAN,
                        ),
                )
            awaitDetails("login-fix")
        }

    @Test
    fun `FR-S3 default options send no model and no mode`() =
        runComposeUiTest {
            // Given the open dialog with only a prompt
            showWindowOf(world.setup)
            openWithProject(world.setup, "project0001")
            typePrompt("Fix the login")

            // When the user starts
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()

            // Then no model, mode, subagent or name was sent
            awaitGone(WindowTags.NEW_AGENT_DIALOG)
            world.starts.specs shouldBe listOf(world.quayside.id to NewSessionSpec("Fix the login"))
        }

    @Test
    fun `FR-S3 start from a global view selects the project of the new session`() =
        runComposeUiTest {
            // Given a global view, where no project is selected
            val palette = showWindowOf(world.setup)
            await(WindowTags.view("working")).performClick()
            openFromPalette(palette)
            await(WindowTags.NEW_AGENT_DIALOG)
            awaitFocus(WindowTags.NEW_AGENT_PROMPT)

            // When the user starts without a project, then picks the first one
            typePrompt("Fix the login")
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()
            await(WindowTags.NEW_AGENT_PROBLEM)
            world.fake.calls.shouldBeEmpty()
            onNodeWithTag(WindowTags.NEW_AGENT_PROJECT).performClick()
            awaitText(WindowTags.NEW_AGENT_PROJECT, "Quayside")
            onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()

            // Then the sidebar shows that project's page with the new session selected
            awaitGone(WindowTags.NEW_AGENT_DIALOG)
            world.fake.calls shouldBe listOf("start project0001")
            waitUntilExactlyOneExists(hasText("Quayside") and hasAnyAncestor(hasTestTag(WindowTags.HEADER)))
            awaitDetails("00000001")
        }

    @Test
    fun `FR-S3 the command is not offered without projects`() =
        runComposeUiTest {
            // Given a window with no projects
            val empty = AgentWorld()
            val palette =
                showWindowOf(
                    WindowSetup(
                        dev.quayside.projects.api
                            .FakeProjectsApi(),
                        empty.fake,
                        starts = empty.starts,
                    ),
                )

            // When the user opens the palette and looks for the command
            runOnIdle { palette.open() }
            await(WindowTags.PALETTE_FIELD).performTextInput("Nowy agent")
            waitForIdle()

            // Then there is no row for it
            onNodeWithTag(WindowTags.paletteRow("sessions.new")).assertDoesNotExist()
        }
}
