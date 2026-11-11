package dev.quayside.missioncontrol.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import dev.quayside.missioncontrol.ui.DiskConversationKit.first
import dev.quayside.missioncontrol.ui.DiskConversationKit.messages
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.MessageRole
import dev.quayside.sessions.api.aSessionMessage
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * The preview (space) of a disk session on fakes (FR-P9, AM-315; docs/12 §4): the messages of its session file with
 * the role as text, "Wczytaj starsze" above them and the note of a cut message. Texts are literal, so a missing
 * resource entry fails the test.
 */
@OptIn(ExperimentalTestApi::class)
internal class DiskPreviewScreenTest {
    private fun ComposeUiTest.openHistory(fake: FakeSessions) {
        showMc(fake)
        awaitTag(MissionControlTags.row(first)).performClick()
        awaitTag(MissionControlTags.DETAILS_HISTORY).performClick()
        awaitTag(MissionControlTags.PREVIEW)
    }

    private fun ComposeUiTest.labelCount(label: String): Int =
        onAllNodes(hasText(label) and inTag(MissionControlTags.PREVIEW)).fetchSemanticsNodes().size

    private fun ComposeUiTest.topOf(text: String): Float =
        onAllNodes(hasText(text) and inTag(MissionControlTags.PREVIEW))
            .fetchSemanticsNodes()
            .first()
            .boundsInRoot.top

    @Test
    fun `FR-P9 disk preview shows role labels as text`() =
        runComposeUiTest {
            // Given a disk session whose file holds a question, an answer and a question
            val fake = DiskConversationKit.world(messages(3))

            // When the user opens its history
            openHistory(fake)

            // Then each message has a text label of its author and its text, and no output was read
            textUnder(MissionControlTags.PREVIEW, "message 3").assertIsDisplayed()
            labelCount("Ty") shouldBe 2
            labelCount("Claude") shouldBe 1
            fake.outputReads.shouldBeEmpty()
        }

    @Test
    fun `FR-P9 disk preview lists the messages oldest first`() =
        runComposeUiTest {
            openHistory(DiskConversationKit.world(messages(3)))

            val tops = (1..3).map { topOf("message $it") }

            tops shouldBe tops.sorted()
            tops.toSet().size shouldBe 3
        }

    @Test
    fun `FR-P9 load older adds earlier messages above and the button goes at the start of the file`() =
        runComposeUiTest {
            // Given 25 messages: the newest 20 are shown, and the button is above them
            openHistory(DiskConversationKit.world(messages(25)))
            textUnder(MissionControlTags.PREVIEW, "message 25").assertExists()
            onAllNodes(hasText("message 5", substring = true) and inTag(MissionControlTags.PREVIEW))
                .assertCountEquals(0)
            val buttonTop = awaitTag(MissionControlTags.PREVIEW_LOAD_OLDER).fetchSemanticsNode().boundsInRoot.top
            (buttonTop < topOf("message 6")) shouldBe true
            textUnder(MissionControlTags.PREVIEW_LOAD_OLDER, "Wczytaj starsze").assertExists()

            // When the user clicks it
            awaitTag(MissionControlTags.PREVIEW_LOAD_OLDER).performClick()

            // Then the earlier messages are there, above the others, and no button is left
            waitUntilDoesNotExist(hasTestTag(MissionControlTags.PREVIEW_LOAD_OLDER))
            (topOf("message 1") < topOf("message 6")) shouldBe true
        }

    @Test
    fun `FR-P9 load older button never takes focus`() =
        runComposeUiTest {
            // Given 45 messages, so that the button stays after one click
            openHistory(DiskConversationKit.world(messages(45)))

            // When the user clicks the button, then walks through the window with Tab
            awaitTag(MissionControlTags.PREVIEW_LOAD_OLDER).performClick()
            awaitTag(MissionControlTags.PREVIEW_LOAD_OLDER).assertIsNotFocused()
            repeat(TABS) {
                onRoot().performKeyInput { pressKey(Key.Tab) }
                awaitTag(MissionControlTags.PREVIEW_LOAD_OLDER).assertIsNotFocused()
            }
        }

    @Test
    fun `FR-P9 load older is hidden at the start of the file`() =
        runComposeUiTest {
            openHistory(DiskConversationKit.world(messages(3)))

            textUnder(MissionControlTags.PREVIEW, "message 1").assertIsDisplayed()
            hasTag(MissionControlTags.PREVIEW_LOAD_OLDER) shouldBe false
        }

    @Test
    fun `FR-P9 truncated message shows the note`() =
        runComposeUiTest {
            val cut = aSessionMessage(2, MessageRole.ASSISTANT, "A very long answer", truncated = true)
            openHistory(DiskConversationKit.world(listOf(aSessionMessage(1), cut, aSessionMessage(3))))

            onAllNodes(hasText("(przycięte)") and inTag(MissionControlTags.PREVIEW)).assertCountEquals(1)
        }

    @Test
    fun `FR-P9 a message that is whole shows no note`() =
        runComposeUiTest {
            openHistory(DiskConversationKit.world(messages(3)))

            onAllNodes(hasText("(przycięte)")).assertCountEquals(0)
        }

    @Test
    fun `FR-P9 a file without messages says so in the preview`() =
        runComposeUiTest {
            openHistory(DiskConversationKit.world(emptyList()))

            textUnder(MissionControlTags.PREVIEW, "Brak wiadomości w pliku sesji").assertIsDisplayed()
        }

    @Test
    fun `FR-P9 a failed read shows the reason and not the empty note`() =
        runComposeUiTest {
            val fake = DiskConversationKit.world(messages(3))
            fake.conversationFiles.failNext()

            openHistory(fake)

            textUnder(MissionControlTags.PREVIEW, "Nie udało się wczytać historii").assertIsDisplayed()
            labelCount("Brak wiadomości w pliku sesji") shouldBe 0
        }

    private companion object {
        const val TABS = 4
    }
}
