@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.TestClock
import dev.quayside.missioncontrol.ui.DiskConversationKit.first
import dev.quayside.missioncontrol.ui.DiskConversationKit.messages
import dev.quayside.missioncontrol.ui.DiskConversationKit.supervisor
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.MessageCursor
import dev.quayside.sessions.api.SessionsApi
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/** The history of a disk session in the state of the controller (AM-315): one source for the panel and the preview. */
class ConversationControllerTest {
    private val log = RecordingLogger()

    private fun TestScope.start(fake: FakeSessions): MissionControlController =
        MissionControlController(
            SessionsApi(monitor = fake, commands = fake),
            FakeProjectsApi(initial = listOf(MC_PROJECT)),
            MutableStateFlow<ProjectFocus?>(ProjectFocus(MC_PROJECT.id)),
            TestClock(),
            backgroundScope,
            log,
            1.hours,
        ).also { runCurrent() }

    private fun TestScope.select(
        controller: MissionControlController,
        id: SessionId,
    ) {
        controller.onEvent(MissionControlEvent.SelectSession(id))
        runCurrent()
    }

    @Test
    fun `selecting a disk session puts its newest messages in the state without opening the preview`() =
        runTest {
            val controller = start(DiskConversationKit.world(messages(25)))

            select(controller, first)

            controller.state.value.conversation
                ?.messages shouldBe messages(25).drop(5)
            controller.state.value.preview
                .shouldBeNull()
        }

    @Test
    fun `a supervisor session has no history in the state`() =
        runTest {
            val controller = start(DiskConversationKit.world(messages(25)))

            select(controller, supervisor)

            controller.state.value.conversation
                .shouldBeNull()
        }

    @Test
    fun `LoadOlder adds the older page to the state`() =
        runTest {
            val controller = start(DiskConversationKit.world(messages(25)))
            select(controller, first)

            controller.onEvent(MissionControlEvent.LoadOlder)
            runCurrent()

            controller.state.value.conversation
                ?.messages shouldBe messages(25)
            controller.state.value.conversation
                ?.older shouldBe null
        }

    @Test
    fun `LoadOlder uses the cursor of the shown page`() =
        runTest {
            val fake = DiskConversationKit.world(messages(45))
            val controller = start(fake)
            select(controller, first)

            controller.onEvent(MissionControlEvent.LoadOlder)
            runCurrent()

            fake.conversationFiles.reads shouldBe listOf(first to null, first to MessageCursor(25))
        }

    @Test
    fun `the controller logs no message text`() =
        runTest {
            val fake = DiskConversationKit.world(messages(3))
            val controller = start(fake)
            select(controller, first)

            log.entries.none { entry ->
                (listOf(entry.message) + entry.fields.values.map { it.toString() }).any { "message 1" in it }
            } shouldBe true
        }
}
