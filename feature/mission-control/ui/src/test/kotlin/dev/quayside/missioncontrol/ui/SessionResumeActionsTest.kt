package dev.quayside.missioncontrol.ui

import dev.quayside.core.designsystem.ButtonKind
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.pastSession
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset

/** Which actions the details panel offers a disk session now that it can be resumed (FR-P9, AM-313). */
internal class SessionResumeActionsTest {
    private val project = aProject(id = "project0001", displayName = "Quayside")
    private val disk = pastSession(id = "aaaa0001-0000-4000-8000-000000000001", project = project.id)
    private val snapshot = SessionsSnapshot(listOf(disk), SnapshotStatus.Fresh(T0))

    @ParameterizedTest(name = "{0}")
    @MethodSource("availableCases")
    fun `available actions by origin and state`(
        @Suppress("UNUSED_PARAMETER") title: String,
        state: SessionState,
        origin: SessionOrigin,
        busy: Boolean,
        expected: Set<SessionAction>,
    ) {
        SessionActions.available(state, busy, origin) shouldBe expected
    }

    @Test
    fun `an open dialog takes resume away from a disk session`() {
        val selection = Selection(project = project.id, session = disk.id)
        val acting = ActionUi(dialog = SessionDialog.ConfirmRemove(disk.id, disk.name, fromDisk = true))

        val shown =
            MissionControlReducer
                .reduce(snapshot, listOf(project), selection, Instant.EPOCH, ZoneOffset.UTC)
                .withActions(snapshot, selection, acting, listOf(project))
                .screen
                .shouldBeInstanceOf<Screen.Project>()
                .details

        shown?.actions shouldBe emptySet()
    }

    @Test
    fun `the panel of a disk session lists resume, then remove, and no preview`() {
        val details = diskDetails(setOf(SessionAction.RESUME, SessionAction.REMOVE))

        details.detailsControls().map { it.id } shouldBe listOf("resume", "remove")
        details.allControls().map { it.id } shouldBe listOf("resume", "remove")
        details.detailsControls().last().kind shouldBe ButtonKind.DANGER
    }

    @Test
    fun `resume is not a dangerous control`() {
        val resume = diskDetails(setOf(SessionAction.RESUME)).detailsControls().single { it.id == "resume" }

        resume.labelKey shouldBe "action.resume"
        resume.event shouldBe MissionControlEvent.Run(SessionAction.RESUME)
        (resume.kind == ButtonKind.DANGER) shouldBe false
    }

    private fun diskDetails(actions: Set<SessionAction>) =
        SessionDetails(
            id = SessionId("aaaa0001-0000-4000-8000-000000000001"),
            name = "Fix the login bug",
            glyph = StatusLook.of(SessionState.Stopped),
            summary = null,
            worktree = Path.of("/Users/test/project"),
            startedAt = T0,
            lastActivityAt = T0,
            actions = actions,
            fromDisk = true,
        )

    companion object {
        @JvmStatic
        @Suppress("LongMethod")
        fun availableCases(): List<Arguments> {
            val resumeAndRemove = setOf(SessionAction.RESUME, SessionAction.REMOVE)
            val respawnAndRemove = setOf(SessionAction.RESPAWN, SessionAction.REMOVE)
            return listOf(
                arguments("disk, stopped", SessionState.Stopped, SessionOrigin.DISK, false, resumeAndRemove),
                arguments("disk, busy", SessionState.Stopped, SessionOrigin.DISK, true, emptySet<SessionAction>()),
                arguments(
                    "supervisor, stopped",
                    SessionState.Stopped,
                    SessionOrigin.SUPERVISOR,
                    false,
                    respawnAndRemove,
                ),
                arguments("supervisor, done", SessionState.Done, SessionOrigin.SUPERVISOR, false, respawnAndRemove),
                arguments("supervisor, failed", SessionState.Failed, SessionOrigin.SUPERVISOR, false, respawnAndRemove),
                arguments(
                    "supervisor, working",
                    SessionState.Working,
                    SessionOrigin.SUPERVISOR,
                    false,
                    setOf(SessionAction.STOP),
                ),
                arguments(
                    "supervisor, stopped, busy",
                    SessionState.Stopped,
                    SessionOrigin.SUPERVISOR,
                    true,
                    emptySet<SessionAction>(),
                ),
            )
        }
    }
}
