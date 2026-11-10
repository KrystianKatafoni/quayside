package dev.quayside.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files
import java.nio.file.Path

/**
 * "Nowy agent" on a group in the whole window on fakes (FR-S3, FR-P7, FR-K1, FR-K2; AM-308): the dialog starts where
 * the sidebar is, and after the start the sidebar and Mission Control stay on that subfolder. The order
 * `ProjectsIntent.Select` then `SelectSession` is wired by `WindowControllers`, so it is tested here (Lessons).
 */
@OptIn(ExperimentalTestApi::class)
class NewAgentSubfolderWindowTest {
    @TempDir
    lateinit var root: Path

    private val plantDir by lazy { Files.createDirectory(root.resolve("plant-puls")) }
    private val plant by lazy {
        aProject(
            id = "project0001",
            dir = plantDir,
            displayName = "plant-puls",
            subfolders = subfolderNames("docs", "soft"),
        )
    }
    private val sessions by lazy {
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(plant.id, plant.dir, subfolders = listOf("docs", "soft"))),
                sessions =
                    listOf(
                        WorldSession(SessionId("rooted"), plantDir, SessionState.Working, "rooted", T0),
                        WorldSession(SessionId("in-docs"), plantDir.resolve("docs"), SessionState.Working, "d", T0),
                    ),
            ),
        )
    }
    private val starts by lazy { RecordingStarts(sessions) }
    private val setup by lazy { WindowSetup(FakeProjectsApi(initial = listOf(plant)), sessions, starts = starts) }
    private val soft get() = WindowTags.subfolderRow("project0001", "soft")

    private fun androidx.compose.ui.test.ComposeUiTest.header(text: String) =
        waitUntilExactlyOneExists(hasText(text) and hasAnyAncestor(hasTestTag(WindowTags.HEADER)), AGENT_WAIT_MS)

    private fun androidx.compose.ui.test.ComposeUiTest.startWithPrompt() {
        typePrompt("Fix the login")
        onNodeWithTag(WindowTags.NEW_AGENT_START).performClick()
        awaitGone(WindowTags.NEW_AGENT_DIALOG)
    }

    @Test
    fun `new agent from a selected subfolder starts there and stays on the subfolder page`() =
        runComposeUiTest {
            // Given the page of the subfolder "soft", clicked in the sidebar and followed at once by cmd N
            showWindowOf(setup)
            await(WindowTags.projectRow("project0001")).performClick()
            await(soft).performClick()
            pressCmdN(setup)
            await(WindowTags.NEW_AGENT_DIALOG)
            awaitText(WindowTags.NEW_AGENT_SUBFOLDER, "soft")

            // When the user starts the agent
            awaitFocus(WindowTags.NEW_AGENT_PROMPT)
            startWithPrompt()

            // Then it started in the subfolder directory, and the window stays on the subfolder page
            starts.specs.map { it.first to it.second.subfolder } shouldBe listOf(plant.id to "soft")
            sessions.snapshot.value.sessions
                .single { it.id == SessionId("00000001") }
                .cwd shouldBe plantDir.resolve("soft")
            await(soft).assertIsSelected()
            await(WindowTags.projectRow("project0001")).assertIsNotSelected()
            header("plant-puls / soft")
            await(WindowTags.missionRow("00000001")).assertIsSelected()
            // and the group row counts it with the two sessions it had before (the subfolder row has no chips)
            awaitChips(WindowTags.projectRow("project0001"), working = 3)
            counters(soft) shouldBe emptyMap()
        }

    @Test
    fun `new agent from the group row starts in the project directory`() =
        runComposeUiTest {
            // Given the group row selected, and the dialog opened on it
            showWindowOf(setup)
            await(WindowTags.projectRow("project0001")).performClick()
            pressCmdN(setup)
            await(WindowTags.NEW_AGENT_DIALOG)
            awaitText(WindowTags.NEW_AGENT_SUBFOLDER, "Katalog projektu")
            awaitFocus(WindowTags.NEW_AGENT_PROMPT)

            // When the user starts the agent
            startWithPrompt()

            // Then it started in the project directory and the group row stays selected
            starts.specs.map { it.second.subfolder } shouldBe listOf(null)
            sessions.snapshot.value.sessions
                .single { it.id == SessionId("00000001") }
                .cwd shouldBe plantDir
            header("plant-puls")
            await(WindowTags.projectRow("project0001")).assertIsSelected()
            await(soft).assertIsNotSelected()
            await(WindowTags.missionRow("00000001")).assertIsSelected()
        }

    @Test
    fun `new agent started in the project directory from a subfolder page selects the project row`() =
        runComposeUiTest {
            // Given the page of the subfolder "soft", and the dialog switched to "Katalog projektu"
            showWindowOf(setup)
            await(WindowTags.projectRow("project0001")).performClick()
            await(soft).performClick()
            pressCmdN(setup)
            await(WindowTags.NEW_AGENT_DIALOG)
            awaitText(WindowTags.NEW_AGENT_SUBFOLDER, "soft")
            pickOption(WindowTags.NEW_AGENT_SUBFOLDER, 0)
            awaitText(WindowTags.NEW_AGENT_SUBFOLDER, "Katalog projektu")

            // When the user starts the agent
            // (the focus stays on the "Podfolder" list after the pick, which is fine: text input needs no focus)
            startWithPrompt()

            // Then it started in the project directory, and the sidebar and Mission Control follow it there
            starts.specs.map { it.second.subfolder } shouldBe listOf(null)
            header("plant-puls")
            await(WindowTags.projectRow("project0001")).assertIsSelected()
            await(soft).assertIsNotSelected()
            await(WindowTags.missionRow("00000001")).assertIsSelected()
        }

    /** Every way in calls the same function, but each is wired by the window itself (Lessons). */
    enum class Way { PALETTE, SHORTCUT, BUTTON }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Way::class)
    fun `every way in opens new agent with the sidebar subfolder`(way: Way) =
        runComposeUiTest {
            val palette = showWindowOf(setup)
            await(WindowTags.projectRow("project0001")).performClick()
            await(soft).performClick()

            when (way) {
                Way.PALETTE -> openFromPalette(palette)
                Way.SHORTCUT -> pressCmdN(setup)
                Way.BUTTON -> await(WindowTags.NEW_AGENT_BUTTON).performClick()
            }

            await(WindowTags.NEW_AGENT_DIALOG)
            awaitText(WindowTags.NEW_AGENT_SUBFOLDER, "soft")
        }
}
