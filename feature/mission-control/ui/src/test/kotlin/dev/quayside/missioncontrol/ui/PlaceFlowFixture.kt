@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldPastSession
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.time.Duration.Companion.hours

/** Fixture of the tests of the list of places: the group `plant-puls`, the sessions and the controller. */
internal open class PlaceFlowFixture {
    protected val group = OVERVIEW_GROUP
    protected val plain = aProject(id = "plain", dir = OVERVIEW_DIR.resolveSibling("plain"), displayName = "plain")
    protected val log = RecordingLogger()
    protected val one = SessionId("sess0001")
    protected val two = SessionId("sess0002")
    protected val disk = SessionId("dddd0003-0000-4000-8000-000000000003")
    protected val carl = "sub.2"

    protected fun supervisor(
        id: SessionId,
        index: Long,
        state: SessionState = SessionState.Working,
        dir: String = "bob/api",
    ) = WorldSession(id, OVERVIEW_DIR.resolve(dir), state, "name-${id.value}", T0.plusSeconds(index))

    protected fun world(): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects =
                    listOf(
                        WorldProject(group.id, group.dir, subfolders = listOf("bob", "Carl", "Anna")),
                        WorldProject(plain.id, plain.dir),
                    ),
                sessions =
                    listOf(
                        supervisor(one, 0),
                        supervisor(two, 1, SessionState.Done),
                        WorldSession(SessionId("lone0001"), plain.dir, SessionState.Working, "lone", T0),
                    ),
                pastSessions = listOf(WorldPastSession(disk, OVERVIEW_DIR, T0.minusSeconds(60), "old chat")),
            ),
            TestClock(T0),
        )

    protected fun TestScope.start(
        fake: FakeSessions,
        commands: SessionCommands = fake,
        shown: Project = group,
    ): MissionControlController =
        MissionControlController(
            SessionsApi(monitor = fake, commands = commands),
            FakeProjectsApi(initial = listOf(group, plain)),
            MutableStateFlow<ProjectFocus?>(ProjectFocus(shown.id)),
            TestClock(T0),
            backgroundScope,
            log,
            1.hours,
        ).also { runCurrent() }

    protected fun TestScope.event(
        controller: MissionControlController,
        event: MissionControlEvent,
    ) {
        controller.onEvent(event)
        runCurrent()
    }

    protected fun TestScope.open(
        controller: MissionControlController,
        id: SessionId,
    ): SessionDialog.AssignPlace {
        event(controller, MissionControlEvent.SelectSession(id))
        event(controller, MissionControlEvent.Run(SessionAction.ASSIGN_PLACE))
        return controller.state.value.dialog
            .shouldBeInstanceOf<SessionDialog.AssignPlace>()
    }

    protected fun MissionControlController.details(): SessionDetails? =
        state.value.screen
            .shouldBeInstanceOf<Screen.Project>()
            .details

    protected fun FakeSessions.dirOf(id: SessionId) =
        snapshot.value.sessions
            .single { it.id == id }
            .assignedDir
}
