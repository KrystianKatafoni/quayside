package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.testing.T0
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import java.nio.file.Files
import java.nio.file.Path

/**
 * The window of the AM-816 tests: two projects ("Quayside" with a real directory, "Other"), a session that waits in
 * each and one that works in "Quayside". Session ids equal their names; `claude attach <id>` "runs" in a temp dir.
 * The window gets the notification settings, so that the voice chip and the notification status are drawn.
 */
@OptIn(ExperimentalTestApi::class)
internal class TopBarWorld(
    root: Path,
    projects: List<String> = listOf("quayside", "other"),
    notifications: NotificationSettings = NotificationSettings(),
) {
    val terminals = RecordingTerminals()
    val repo = FakeNotificationSettingsRepo(notifications)
    val quayside: Project =
        aProject(id = "project0001", dir = Files.createDirectory(root.resolve("quayside")), displayName = "Harbor")
    val other: Project =
        aProject(id = "project0002", dir = A_PROJECT_DIR.resolveSibling("other"), displayName = "Other")
    private val listed = projects.map { if (it == "quayside") quayside else other }

    val sessions =
        FakeSessions(
            SessionsWorld(
                listOf(WorldProject(quayside.id, quayside.dir), WorldProject(other.id, other.dir)),
                listOf(
                    WorldSession(
                        SessionId("fix-login"),
                        quayside.dir,
                        SessionState.AwaitingInput(null),
                        "fix-login",
                        T0,
                    ),
                    WorldSession(SessionId("popup1"), quayside.dir, SessionState.Working, "popup1", T0.plusSeconds(1)),
                    WorldSession(
                        SessionId("their-ask"),
                        other.dir,
                        SessionState.AwaitingInput(null),
                        "their-ask",
                        T0.plusSeconds(2),
                    ),
                ),
            ),
        )

    lateinit var setup: WindowSetup

    private val tempDir = Path.of(System.getProperty("java.io.tmpdir"))

    private fun attachCommand(id: SessionId) = ShellCommand(listOf("claude", "attach", id.value), tempDir)

    fun ComposeUiTest.window(supervisor: String? = null): CommandPaletteController {
        setup =
            WindowSetup(
                FakeProjectsApi(initial = listed),
                sessions,
                terminals,
                ::attachCommand,
                SettingsRepos(notifications = repo),
            ).also { it.supervisor = supervisor }
        return showWindowOf(setup)
    }

    fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(WindowTags.KEYS).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    fun ComposeUiTest.has(tag: String): Boolean = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** A text inside the node tagged [tag], or on the node itself. */
    fun ComposeUiTest.textIn(
        tag: String,
        text: String,
    ): Boolean =
        onAllNodes(hasText(text, substring = true) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))
            .fetchSemanticsNodes()
            .isNotEmpty()

    /** Waits until the node tagged [tag] shows [text]. */
    fun ComposeUiTest.awaitText(
        tag: String,
        text: String,
    ) {
        waitUntil { textIn(tag, text) }
    }

    /** Selects "Quayside" and its session "fix-login" and attaches it with Enter: the list mode with one tab. */
    fun ComposeUiTest.attached() {
        await(WindowTags.projectRow("project0001")).performClick()
        await(WindowTags.missionRow("fix-login")).performClick()
        await(WindowTags.DETAILS_PREVIEW)
        waitForIdle()
        press(Key.Enter)
        await(WindowTags.terminalTab(1))
    }

    /** The full screen terminal mode with the attached session. */
    fun ComposeUiTest.terminalMode() {
        attached()
        await(WindowTags.FULL_SCREEN).performClick()
        await(WindowTags.RAIL)
    }

    fun ComposeUiTest.awaitGone(tag: String) = waitUntilDoesNotExist(hasTestTag(tag))

    fun ComposeUiTest.awaitSome(tag: String) = waitUntilAtLeastOneExists(hasTestTag(tag))
}
