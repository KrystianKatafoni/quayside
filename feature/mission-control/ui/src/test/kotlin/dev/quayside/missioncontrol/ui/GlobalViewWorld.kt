@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Two projects and sessions in both and outside any project, for the controller tests of the global views
 * (AM-407). Sessions start at T0 + their index, so the newest has the highest index and is the first row.
 */
internal class GlobalViewWorld(
    sessions: List<WorldSession>,
    selected: ProjectId? = PROJECT.id,
) {
    val clock = TestClock()
    val projects = FakeProjectsApi(initial = listOf(PROJECT, OTHER))
    val fake =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(PROJECT.id, PROJECT.dir), WorldProject(OTHER.id, OTHER.dir)),
                sessions = sessions,
            ),
            clock,
        )
    val selected = MutableStateFlow(selected)

    fun TestScope.start(
        tick: Duration = 1.hours,
        commands: SessionCommands = fake,
        logger: Logger = NoOpLogger,
    ): MissionControlController =
        MissionControlController(
            SessionsApi(monitor = fake, commands = commands),
            projects,
            this@GlobalViewWorld.selected,
            clock,
            backgroundScope,
            logger,
            tick,
        ).also { runCurrent() }

    companion object {
        val PROJECT = aProject(displayName = "Quayside")
        val OTHER = aProject(id = "other", dir = Path.of("/Users/test/projects/other"), displayName = "Other")
        val ELSEWHERE: Path = Path.of("/Users/test/elsewhere")

        fun session(
            id: String,
            index: Long,
            state: SessionState = SessionState.Working,
            cwd: Path = A_PROJECT_DIR,
        ): WorldSession =
            WorldSession(
                id = SessionId(id),
                cwd = cwd,
                state = state,
                name = "name-$id",
                startedAt = T0.plusSeconds(index),
            )

        /** Working sessions in the project, in the other project and outside any project, in this start order. */
        fun working(): List<WorldSession> =
            listOf(
                session("mine", 1),
                session("theirs", 2, cwd = OTHER.dir),
                session("nowhere", 3, cwd = ELSEWHERE),
            )
    }
}

internal fun MissionControlController.global(): Screen.Global = state.value.screen.shouldBeInstanceOf<Screen.Global>()

internal fun MissionControlController.projectPage(): Screen.Project =
    state.value.screen.shouldBeInstanceOf<Screen.Project>()

internal fun MissionControlController.globalIds(): List<String> = global().rows.map { it.id.value }

internal fun MissionControlController.globalSelected(): String? =
    global()
        .rows
        .singleOrNull { it.selected }
        ?.id
        ?.value
