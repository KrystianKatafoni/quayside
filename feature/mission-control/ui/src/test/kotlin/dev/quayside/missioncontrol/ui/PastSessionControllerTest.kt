@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldPastSession
import dev.quayside.sessions.api.WorldProject
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/**
 * The controller on a disk session (FR-P9) over `FakeSessions`, which passes the same contract as the real system:
 * "Wznów" (AM-313, see SessionResumeControllerTest) and "Usuń" work; the keys of the other actions do nothing.
 */
class PastSessionControllerTest {
    private val project = aProject(displayName = "Quayside")
    private val log = RecordingLogger()
    private val uuid = SessionId("00000000-0000-4000-8000-000000000001")
    private val other = SessionId("00000000-0000-4000-8000-000000000002")

    private fun fake(): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(project.id, project.dir)),
                pastSessions =
                    listOf(
                        WorldPastSession(uuid, A_PROJECT_DIR, TestClock().now().minusSeconds(60), "Old chat"),
                        WorldPastSession(other, A_PROJECT_DIR, TestClock().now().minusSeconds(120), "Older chat"),
                    ),
            ),
            TestClock(),
        )

    private fun TestScope.start(
        fake: FakeSessions,
        focus: MutableStateFlow<ProjectFocus?> = MutableStateFlow(ProjectFocus(project.id)),
    ): MissionControlController =
        MissionControlController(
            SessionsApi(monitor = fake, commands = fake),
            FakeProjectsApi(initial = listOf(project)),
            focus,
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

    private fun TestScope.send(
        controller: MissionControlController,
        intent: MissionControlIntent,
    ) {
        controller.onIntent(intent)
        runCurrent()
    }

    private fun MissionControlController.page(): Screen.Project =
        state.value.screen.shouldBeInstanceOf<Screen.Project>()

    @Test
    fun `a disk session is selected like any other and shows resume and remove`() =
        runTest {
            val controller = start(fake())

            select(controller, uuid)

            controller.page().details?.actions shouldBe setOf(SessionAction.RESUME, SessionAction.REMOVE)
            controller.page().details?.name shouldBe "Old chat"
        }

    @Test
    fun `attach, reply and preview are not offered for a disk session`() =
        runTest {
            val controller = start(fake())
            select(controller, uuid)

            val actions = controller.actions.value

            actions.canAttach shouldBe false
            actions.canReply shouldBe false
            actions.canPreview shouldBe false
            actions.canStop shouldBe false
            actions.canRespawn shouldBe false
            actions.canRemove shouldBe true
            actions.canResume shouldBe true
            controller.attachTarget().shouldBeNull()
        }

    @Test
    fun `respawn and stop on a disk session call nothing`() =
        runTest {
            val fake = fake()
            val controller = start(fake)
            select(controller, uuid)

            send(controller, MissionControlIntent.RespawnSelected)
            send(controller, MissionControlIntent.StopSelected)

            fake.calls.shouldBeEmpty()
            controller
                .page()
                .details
                ?.failure
                .shouldBeNull()
        }

    @Test
    fun `the preview does not open for a disk session`() =
        runTest {
            val fake = fake()
            val controller = start(fake)
            select(controller, uuid)

            send(controller, MissionControlIntent.TogglePreview)

            controller.state.value.preview
                .shouldBeNull()
            fake.outputReads.shouldBeEmpty()
        }

    @Test
    fun `remove asks first, then hides the session and leaves the others`() =
        runTest {
            val fake = fake()
            val controller = start(fake)
            select(controller, uuid)

            send(controller, MissionControlIntent.RemoveSelected)
            controller.state.value.dialog shouldBe SessionDialog.ConfirmRemove(uuid, "Old chat", fromDisk = true)
            fake.calls.shouldBeEmpty()
            controller.onEvent(MissionControlEvent.ConfirmRemove)
            runCurrent()

            fake.calls shouldBe listOf("remove ${uuid.value}")
            controller.page().rows.map { it.id } shouldBe listOf(other)
            fake.historySettings.settings.value.hidden shouldBe setOf(uuid)
        }

    @Test
    fun `showFinished keeps the filter when the sidebar selects the project a moment later`() =
        runTest {
            val focus = MutableStateFlow<ProjectFocus?>(null)
            val controller = start(fake(), focus)

            controller.showFinished(project.id)
            runCurrent()
            focus.value = ProjectFocus(project.id)
            runCurrent()

            controller.page().filter shouldBe SessionFilter.FINISHED
            controller.page().rows.map { it.id } shouldBe listOf(uuid, other)
        }

    @Test
    fun `a removed disk session does not come back with the next snapshot`() =
        runTest {
            val fake = fake()
            val controller = start(fake)
            select(controller, uuid)
            send(controller, MissionControlIntent.RemoveSelected)
            controller.onEvent(MissionControlEvent.ConfirmRemove)
            runCurrent()

            fake.addPastSession(WorldPastSession(SessionId("00000000-0000-4000-8000-000000000003"), A_PROJECT_DIR))
            runCurrent()

            controller
                .page()
                .rows
                .map { it.id.value }
                .toSet() shouldBe
                setOf(other.value, "00000000-0000-4000-8000-000000000003")
            controller.page().details.shouldBeNull()
        }
}
