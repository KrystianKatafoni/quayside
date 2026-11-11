package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionMessage
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldPastSession
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.aSessionMessage
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The name, the summary and the history of an old session from disk in the whole window on fakes (FR-P9, AM-315,
 * docs/12 §4). `WindowKeys` stands on a focused `Box` (see `showWindow`); texts are literal.
 */
@OptIn(ExperimentalTestApi::class)
class DiskConversationWindowTest {
    private val agent = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private val disk = SessionId("00000000-0000-4000-8000-000000000001")

    private fun messages(count: Int): List<SessionMessage> = (1..count).map { aSessionMessage(it) }

    private fun fake(
        history: List<SessionMessage>,
        file: (WorldPastSession) -> WorldPastSession = { it },
    ): FakeSessions {
        val clock = TestClock()
        val past = WorldPastSession(disk, A_PROJECT_DIR, clock.now().minusSeconds(60), "First question")
        return FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(agent.id, agent.dir)),
                pastSessions = listOf(file(past)),
            ),
            clock,
        ).also { it.conversationFiles.set(disk, history) }
    }

    private fun ComposeUiTest.press(key: Key) {
        onNodeWithTag(WindowTags.KEYS).performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.openDisk(sessions: FakeSessions) {
        showWindow(FakeProjectsApi(initial = listOf(agent)), sessions)
        await(WindowTags.projectRow("project0001")).performClick()
        await(WindowTags.missionRow(disk.value)).performClick()
        await(WindowTags.DETAILS)
        waitForIdle()
    }

    private fun ComposeUiTest.awaitText(
        text: String,
        tag: String,
    ) {
        waitUntil { shows(tag, text) }
    }

    @Test
    fun `FR-P9 disk session shows its file name and summary`() =
        runComposeUiTest {
            // Given an old session whose file has a name and a first and a last user message
            val sessions =
                fake(messages(3)) { it.copy(name = "Named by Claude", lastPrompt = "Last question") }

            // When the user opens the project and selects it
            openDisk(sessions)

            // Then the row shows the name and the summary, and so does the panel
            shows(WindowTags.missionRow(disk.value), "Named by Claude") shouldBe true
            shows(WindowTags.missionRow(disk.value), "First question → Last question") shouldBe true
            shows(WindowTags.DETAILS, "Named by Claude") shouldBe true
            shows(WindowTags.DETAILS_SUMMARY, "First question → Last question") shouldBe true
        }

    @Test
    fun `FR-P9 space on a disk session shows the last messages`() =
        runComposeUiTest {
            // Given a selected old session whose file holds three messages
            val sessions = fake(messages(3))
            openDisk(sessions)

            // When the user presses space
            press(Key.Spacebar)

            // Then the preview shows them with the author as text, and reads no output
            await(WindowTags.PREVIEW)
            awaitText("message 3", WindowTags.PREVIEW)
            shows(WindowTags.PREVIEW, "Ty") shouldBe true
            shows(WindowTags.PREVIEW, "Claude") shouldBe true
            sessions.outputReads shouldBe emptyList()
        }

    @Test
    fun `FR-P9 load older adds earlier messages above`() =
        runComposeUiTest {
            // Given the preview of an old session with 25 messages: the newest 20 are shown
            openDisk(fake(messages(25)))
            press(Key.Spacebar)
            await(WindowTags.PREVIEW)
            awaitText("message 25", WindowTags.PREVIEW)
            shows(WindowTags.PREVIEW, "message 3") shouldBe false

            // When the user clicks "Wczytaj starsze"
            await(WindowTags.PREVIEW_LOAD_OLDER).performClick()

            // Then the earliest messages are there ("message 3" is only in the older page; "message 1" is also a
            // prefix of 10 to 19, so it is not waited for)
            awaitText("message 3", WindowTags.PREVIEW)
            shows(WindowTags.PREVIEW, "message 1") shouldBe true
        }

    @Test
    fun `FR-P9 details panel shows the latest messages of a disk session`() =
        runComposeUiTest {
            // Given an old session whose file holds five messages
            openDisk(fake(messages(5)))

            // Then the panel lists the last three, and the button opens the history
            awaitText("message 5", WindowTags.DETAILS_MESSAGES)
            shows(WindowTags.DETAILS_MESSAGES, "message 3") shouldBe true
            shows(WindowTags.DETAILS_MESSAGES, "message 2") shouldBe false
            await(WindowTags.DETAILS_HISTORY).performClick()
            await(WindowTags.PREVIEW)
        }
}
