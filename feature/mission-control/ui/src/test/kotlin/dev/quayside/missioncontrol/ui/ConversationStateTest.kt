@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.missioncontrol.ui.DiskConversationKit.first
import dev.quayside.missioncontrol.ui.DiskConversationKit.messages
import dev.quayside.missioncontrol.ui.DiskConversationKit.second
import dev.quayside.missioncontrol.ui.DiskConversationKit.supervisor
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.sessions.api.MessageCursor
import dev.quayside.sessions.api.SessionMonitor
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * `ConversationState` (AM-315, plan: UI): the newest page when a disk session is selected, older pages on request.
 * A gate in front of the monitor keeps a read open, so "while a page loads" is a state the test can look at.
 */
class ConversationStateTest {
    private val selection = MutableStateFlow(Selection(project = MC_PROJECT.id))
    private val failed = UiText("action.reason.runtime.COMMAND_FAILED")

    private fun TestScope.state(monitor: SessionMonitor): ConversationState =
        ConversationState(monitor, selection, MutableStateFlow(listOf(MC_PROJECT)), backgroundScope)
            .also { runCurrent() }

    private fun TestScope.select(id: SessionId?) {
        selection.value = selection.value.copy(session = id)
        runCurrent()
    }

    private fun ConversationState.texts(): List<String> =
        shown.value
            ?.messages
            ?.map { it.text }
            .orEmpty()

    private fun numbered(range: IntRange): List<String> = range.map { "message $it" }

    @Test
    fun `selecting a disk session reads the newest page once`() =
        runTest {
            val fake = DiskConversationKit.world(messages(25))
            val state = state(fake)

            select(first)
            // a new snapshot (a session appears) must not read again
            fake.appear(WorldSession(SessionId("aaaa0001"), A_PROJECT_DIR))
            runCurrent()

            fake.conversationFiles.reads shouldBe listOf(first to null)
            state.texts() shouldBe numbered(6..25)
            state.shown.value?.older shouldBe MessageCursor(5)
            state.shown.value?.loading shouldBe false
            state.shown.value
                ?.failure
                .shouldBeNull()
        }

    @Test
    fun `the first page shows as loading until it arrives`() =
        runTest {
            val gated = GatedConversation(DiskConversationKit.world(messages(3)))
            gated.holdAll()
            val state = state(gated)

            select(first)

            state.shown.value shouldBe ConversationUi(first, loading = true)
            gated.release()
            runCurrent()
            state.shown.value?.loading shouldBe false
            state.texts() shouldBe numbered(1..3)
        }

    @Test
    fun `supervisor session reads nothing`() =
        runTest {
            val fake = DiskConversationKit.world()
            val state = state(fake)

            select(supervisor)

            fake.conversationFiles.reads shouldBe emptyList()
            state.shown.value.shouldBeNull()
        }

    @Test
    fun `load older prepends the older page`() =
        runTest {
            val fake = DiskConversationKit.world(messages(25))
            val state = state(fake)
            select(first)

            state.loadOlder()
            runCurrent()

            state.texts() shouldBe numbered(1..25)
            state.shown.value
                ?.older
                .shouldBeNull()
            state.shown.value?.loading shouldBe false
            fake.conversationFiles.reads shouldBe listOf(first to null, first to MessageCursor(5))
        }

    @Test
    fun `LoadOlder is ignored while a page loads`() =
        runTest {
            val gated = GatedConversation(DiskConversationKit.world(messages(45)))
            val state = state(gated)
            select(first)
            gated.holdOlder()

            state.loadOlder()
            runCurrent()
            state.loadOlder()
            state.loadOlder()
            runCurrent()

            gated.started shouldBe listOf(first to null, first to MessageCursor(25))
            state.shown.value?.loading shouldBe true
            gated.release()
            runCurrent()
            state.texts() shouldBe numbered(6..45)
        }

    @Test
    fun `LoadOlder is ignored when older is null`() =
        runTest {
            val fake = DiskConversationKit.world(messages(3))
            val state = state(fake)
            select(first)

            state.loadOlder()
            runCurrent()

            fake.conversationFiles.reads shouldBe listOf(first to null)
            state.texts() shouldBe numbered(1..3)
        }

    @Test
    fun `LoadOlder is ignored with no disk session selected`() =
        runTest {
            val fake = DiskConversationKit.world()
            val state = state(fake)
            select(supervisor)

            state.loadOlder()
            runCurrent()

            fake.conversationFiles.reads shouldBe emptyList()
        }

    @Test
    fun `older page from before reselecting the session is dropped`() =
        runTest {
            // Given session A with its newest page shown and a read of an older page that stays open
            val gated = GatedConversation(DiskConversationKit.world(messages(25), messages(2)))
            val state = state(gated)
            select(first)
            gated.holdOlder()
            state.loadOlder()
            runCurrent()

            // When the user goes to B and back to A (a fresh newest page of A is read), and the old read ends
            select(second)
            select(first)
            gated.release()
            runCurrent()

            // Then A shows only its fresh newest page: the late older page is not added
            state.shown.value shouldBe
                ConversationUi(first, messages(25).drop(5), MessageCursor(5), loading = false, failure = null)
        }

    @Test
    fun `first page of an earlier selection that ends late is never shown`() =
        runTest {
            // Given the read of A that ignores the cancel and stays open, and a record of everything the state shows
            val gated = GatedConversation(DiskConversationKit.world(messages(25), messages(2)))
            gated.holdFirstOf(first)
            val state = state(gated)
            val seen = mutableListOf<ConversationUi?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.shown.collect { seen += it } }
            select(first)

            // When the user goes to B (it waits for the read of A, as collectLatest joins it) and the read of A ends
            select(second)
            gated.release()
            runCurrent()

            // Then B is shown, and the page of A was not shown at any moment
            state.shown.value shouldBe
                ConversationUi(second, messages(2), older = null, loading = false, failure = null)
            seen.filterNotNull().filter { it.id == first }.any { it.messages.isNotEmpty() } shouldBe false
        }

    @Test
    fun `failed older page keeps the messages and shows the reason`() =
        runTest {
            val fake = DiskConversationKit.world(messages(25))
            val state = state(fake)
            select(first)
            fake.conversationFiles.failNext()

            state.loadOlder()
            runCurrent()

            state.texts() shouldBe numbered(6..25)
            state.shown.value?.older shouldBe MessageCursor(5)
            state.shown.value?.failure shouldBe failed
            state.shown.value?.loading shouldBe false

            // and a retry works and clears the reason
            state.loadOlder()
            runCurrent()
            state.texts() shouldBe numbered(1..25)
            state.shown.value
                ?.failure
                .shouldBeNull()
        }

    @Test
    fun `failed first page shows the reason and no messages`() =
        runTest {
            val fake = DiskConversationKit.world(messages(5))
            val state = state(fake)
            fake.conversationFiles.failNext()

            select(first)

            state.shown.value shouldBe ConversationUi(first, emptyList(), null, loading = false, failure = failed)
        }
}
