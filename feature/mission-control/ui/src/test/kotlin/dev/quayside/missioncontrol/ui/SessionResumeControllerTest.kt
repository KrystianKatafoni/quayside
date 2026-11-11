@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.ResumeProblem
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldPastSession
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.hours

/**
 * ResumeSelected on a disk session (FR-P9, AM-313) over `FakeSessions`, which passes the same `ResumeContract` as the
 * real system. The first 8 characters of the two UUIDs differ: they are the supervisor's ids of the live sessions.
 */
internal class SessionResumeControllerTest {
    private val project = aProject(displayName = "Quayside")
    private val log = RecordingLogger()
    private val uuid = SessionId("aaaa0001-0000-4000-8000-000000000001")
    private val other = SessionId("bbbb0002-0000-4000-8000-000000000002")
    private val live = SessionId("aaaa0001")

    private fun world(vararg supervisor: WorldSession): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(project.id, project.dir)),
                sessions = supervisor.toList(),
                pastSessions =
                    listOf(
                        WorldPastSession(uuid, A_PROJECT_DIR.resolve("secret-folder"), T0.minusSeconds(60), "Old chat"),
                        WorldPastSession(other, A_PROJECT_DIR, T0.minusSeconds(120), "Older chat"),
                    ),
            ),
            TestClock(T0),
        )

    private fun TestScope.start(
        fake: FakeSessions,
        commands: SessionCommands = fake,
    ): MissionControlController =
        MissionControlController(
            SessionsApi(monitor = fake, commands = commands),
            FakeProjectsApi(initial = listOf(project)),
            MutableStateFlow<ProjectFocus?>(ProjectFocus(project.id)),
            TestClock(T0),
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
    fun `resume selects the live session under all`() =
        runTest {
            val fake = world()
            val controller = start(fake)
            // "Zakończone" does not list a live session, so only a reset of the filter keeps the selection
            controller.onEvent(MissionControlEvent.SelectFilter(SessionFilter.FINISHED))
            select(controller, uuid)

            send(controller, MissionControlIntent.ResumeSelected)

            fake.calls shouldBe listOf("resume ${uuid.value}")
            assertSoftly {
                controller.page().filter shouldBe SessionFilter.ALL
                controller.page().details?.id shouldBe live
                controller.page().details?.name shouldBe "Old chat"
                controller.page().details?.actions shouldBe setOf(SessionAction.STOP)
                controller
                    .page()
                    .details
                    ?.notice
                    .shouldBeNull()
                controller.page().details?.fromDisk shouldBe false
                controller.page().rows.map { it.id } shouldBe listOf(live, other)
            }
        }

    @Test
    fun `resume result does not move a selection changed meanwhile`() =
        runTest {
            val fake = world()
            val gated = GatedCommands(fake)
            val controller = start(fake, gated)
            controller.onEvent(MissionControlEvent.SelectFilter(SessionFilter.FINISHED))
            select(controller, uuid)
            send(controller, MissionControlIntent.ResumeSelected)
            gated.started shouldBe listOf("resume ${uuid.value}")

            select(controller, other)
            gated.gate.complete(Unit)
            runCurrent()

            // the live session exists, but the user has moved on: neither the selection nor the filter jump
            fake.snapshot.value.sessions
                .any { it.id == live } shouldBe true
            assertSoftly {
                controller.page().details?.id shouldBe other
                controller.page().filter shouldBe SessionFilter.FINISHED
            }
        }

    @Test
    fun `failed resume keeps the disk row selected and shows the reason`() =
        runTest {
            val fake = world()
            fake.failNext(SessionError.ResumeUnavailable(uuid, ResumeProblem.FILE_MISSING))
            val controller = start(fake)
            select(controller, uuid)

            send(controller, MissionControlIntent.ResumeSelected)

            assertSoftly {
                controller.page().details?.id shouldBe uuid
                controller.page().details?.failure shouldBe
                    Failure(uuid, SessionAction.RESUME, UiText("action.reason.resume.FILE_MISSING"))
                controller
                    .page()
                    .details
                    ?.busy
                    .shouldBeNull()
                controller.page().details?.actions shouldBe setOf(SessionAction.RESUME, SessionAction.REMOVE)
                controller.page().rows.map { it.id } shouldBe listOf(uuid, other)
            }
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failureTexts")
    fun `each resume problem has its own text`(
        @Suppress("UNUSED_PARAMETER") title: String,
        error: SessionError,
        key: String,
        category: String,
    ) = runTest {
        val fake = world()
        fake.failNext(error)
        val controller = start(fake)
        select(controller, uuid)

        send(controller, MissionControlIntent.ResumeSelected)

        val reason =
            controller
                .page()
                .details
                ?.failure
                ?.reason
        reason shouldBe UiText(key)
        MissionControlStrings.format(reason ?: UiText("missing")).shouldNotBeBlank()
        MissionControlStrings.format(reason ?: UiText("missing")).apply {
            shouldNotContain("Old chat")
            shouldNotContain("secret-folder")
            shouldNotContain(uuid.value)
        }
        val warning = log.entries.single { it.level == LogLevel.WARN }
        warning.fields["reason"] shouldBe category
        log.entries.toString().apply {
            shouldNotContain("Old chat")
            shouldNotContain("secret-folder")
        }
    }

    @Test
    fun `the texts of resume exist`() {
        MissionControlStrings.keys shouldContainAll
            listOf(
                "action.resume",
                "action.resume.busy",
                "action.resume.failed",
                "action.resume.copyNotice",
                "action.reason.resume.FILE_MISSING",
                "action.reason.resume.DIRECTORY_MISSING",
            )
        MissionControlStrings["action.reason.resume.FILE_MISSING"] shouldNotBe
            MissionControlStrings["action.reason.resume.DIRECTORY_MISSING"]
    }

    @Test
    fun `ResumeSelected is ignored for a supervisor session`() =
        runTest {
            val stopped = WorldSession(SessionId("sup00001"), A_PROJECT_DIR, SessionState.Stopped, name = "stopped")
            val fake = world(stopped)
            val controller = start(fake)
            select(controller, stopped.id)

            send(controller, MissionControlIntent.ResumeSelected)

            fake.calls.shouldBeEmpty()
            controller
                .page()
                .details
                ?.failure
                .shouldBeNull()
            controller.actions.value.canResume shouldBe false
        }

    @Test
    fun `ResumeSelected without a selection calls nothing`() =
        runTest {
            val fake = world()
            val controller = start(fake)

            send(controller, MissionControlIntent.ResumeSelected)

            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `no other action is offered while resume runs`() =
        runTest {
            val fake = world()
            val gated = GatedCommands(fake)
            val controller = start(fake, gated)
            select(controller, uuid)

            send(controller, MissionControlIntent.ResumeSelected)

            assertSoftly {
                controller.page().details?.busy shouldBe SessionAction.RESUME
                controller.page().details?.actions shouldBe emptySet()
                controller.actions.value.canResume shouldBe false
                controller.actions.value.canRemove shouldBe false
            }
            send(controller, MissionControlIntent.ResumeSelected)
            send(controller, MissionControlIntent.RemoveSelected)
            gated.started shouldBe listOf("resume ${uuid.value}")
            controller.state.value.dialog
                .shouldBeNull()
        }

    @Test
    fun `copy shows the copy notice and keeps the disk row`() =
        runTest {
            val fake = world()
            fake.opensElsewhere(uuid)
            val controller = start(fake)
            select(controller, uuid)

            send(controller, MissionControlIntent.ResumeSelected)

            val copy =
                fake.snapshot.value.sessions
                    .single { it.origin == SessionOrigin.SUPERVISOR }
                    .id
            assertSoftly {
                controller.page().details?.id shouldBe copy
                controller.page().details?.notice shouldBe UiText("action.resume.copyNotice")
                controller
                    .page()
                    .rows
                    .map { it.id }
                    .contains(uuid) shouldBe true
            }
        }

    @Test
    fun `the copy notice goes away when another session is selected`() =
        runTest {
            val fake = world()
            fake.opensElsewhere(uuid)
            val controller = start(fake)
            select(controller, uuid)
            send(controller, MissionControlIntent.ResumeSelected)

            val copy =
                fake.snapshot.value.sessions
                    .single { it.origin == SessionOrigin.SUPERVISOR }
                    .id

            select(controller, other)
            // the notice is tied to the selection that showed it, not only to the id of the copy
            select(controller, copy)

            controller
                .page()
                .details
                ?.notice
                .shouldBeNull()
        }

    @Test
    fun `actions state offers resume for a disk session only`() =
        runTest {
            val stopped = WorldSession(SessionId("sup00001"), A_PROJECT_DIR, SessionState.Stopped, name = "stopped")
            val controller = start(world(stopped))

            select(controller, uuid)
            val onDisk = controller.actions.value.canResume
            select(controller, stopped.id)
            val onSupervisor = controller.actions.value.canResume

            onDisk shouldBe true
            onSupervisor shouldBe false
        }

    companion object {
        private val uuid = SessionId("aaaa0001-0000-4000-8000-000000000001")

        @JvmStatic
        fun failureTexts(): List<Arguments> =
            listOf(
                arguments(
                    "file missing",
                    SessionError.ResumeUnavailable(uuid, ResumeProblem.FILE_MISSING),
                    "action.reason.resume.FILE_MISSING",
                    "resumeUnavailable.FILE_MISSING",
                ),
                arguments(
                    "directory missing",
                    SessionError.ResumeUnavailable(uuid, ResumeProblem.DIRECTORY_MISSING),
                    "action.reason.resume.DIRECTORY_MISSING",
                    "resumeUnavailable.DIRECTORY_MISSING",
                ),
                arguments(
                    "workspace not trusted",
                    SessionError.Runtime(RuntimeProblem.WORKSPACE_NOT_TRUSTED),
                    "action.reason.runtime.WORKSPACE_NOT_TRUSTED",
                    "runtime.WORKSPACE_NOT_TRUSTED",
                ),
                arguments(
                    "unknown session",
                    SessionError.UnknownSession(uuid),
                    "action.reason.unknownSession",
                    "unknownSession",
                ),
            )
    }
}
