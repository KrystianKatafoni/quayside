package dev.quayside.missioncontrol.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.missioncontrol.ui.DiskConversationKit.first
import dev.quayside.missioncontrol.ui.DiskConversationKit.messages
import dev.quayside.missioncontrol.ui.DiskConversationKit.supervisor
import dev.quayside.sessions.api.FakeSessions
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The details panel of a disk session (FR-P9, AM-315): summary or a fixed fallback, the last three messages and
 * "Pokaż historię". Texts are literal, so a missing resource entry fails the test.
 */
@OptIn(ExperimentalTestApi::class)
internal class DiskDetailsScreenTest {
    private fun ComposeUiTest.pick(fake: FakeSessions) {
        showMc(fake)
        awaitTag(MissionControlTags.row(first)).performClick()
        awaitTag(MissionControlTags.DETAILS)
    }

    private fun ComposeUiTest.panelCount(text: String): Int =
        onAllNodes(hasText(text, substring = true) and inTag(MissionControlTags.DETAILS)).fetchSemanticsNodes().size

    @Test
    fun `FR-P9 disk session shows the last three messages`() =
        runComposeUiTest {
            // Given a disk session whose file holds five messages
            pick(DiskConversationKit.world(messages(5)))

            // Then the panel has the heading and the last three, not the earlier ones
            textUnder(MissionControlTags.DETAILS_MESSAGES, "OSTATNIE WIADOMOŚCI").assertIsDisplayed()
            listOf(3, 4, 5).forEach { textUnder(MissionControlTags.DETAILS_MESSAGES, "message $it").assertExists() }
            onAllNodes(hasText("message 2", substring = true) and inTag(MissionControlTags.DETAILS_MESSAGES))
                .assertCountEquals(0)
        }

    @Test
    fun `FR-P9 disk session without messages shows the fallback summary`() =
        runComposeUiTest {
            // Given a file with no user message and no messages to show
            pick(DiskConversationKit.world(emptyList(), summary = null to null))

            // Then the panel says so in a fixed text, and has no list of messages
            textUnder(MissionControlTags.DETAILS_SUMMARY, "Brak wiadomości w pliku sesji").assertIsDisplayed()
            hasTag(MissionControlTags.DETAILS_MESSAGES) shouldBe false
        }

    @Test
    fun `FR-P9 disk session shows its summary instead of the fallback`() =
        runComposeUiTest {
            pick(DiskConversationKit.world(messages(3), summary = "First question" to "Last question"))

            textUnder(MissionControlTags.DETAILS_SUMMARY, "First question → Last question").assertIsDisplayed()
            panelCount("Brak wiadomości w pliku sesji") shouldBe 0
        }

    @Test
    fun `FR-P9 show history opens the preview and does not take the focus`() =
        runComposeUiTest {
            pick(DiskConversationKit.world(messages(3)))
            textUnder(MissionControlTags.DETAILS_HISTORY, "Pokaż historię").assertIsDisplayed()

            awaitTag(MissionControlTags.DETAILS_HISTORY).performClick()

            awaitTag(MissionControlTags.PREVIEW)
            awaitTag(MissionControlTags.DETAILS_HISTORY).assertIsNotFocused()
        }

    @Test
    fun `FR-P9 a supervisor session has neither history nor messages in its panel`() =
        runComposeUiTest {
            showMc(DiskConversationKit.world())
            awaitTag(MissionControlTags.row(supervisor)).performClick()
            awaitTag(MissionControlTags.DETAILS)

            hasTag(MissionControlTags.DETAILS_HISTORY) shouldBe false
            hasTag(MissionControlTags.DETAILS_MESSAGES) shouldBe false
            // no summary and no placeholder: "no messages in the file" is said only of a session that has a file
            hasTag(MissionControlTags.DETAILS_SUMMARY) shouldBe false
            panelCount("Brak wiadomości w pliku sesji") shouldBe 0
        }
}
