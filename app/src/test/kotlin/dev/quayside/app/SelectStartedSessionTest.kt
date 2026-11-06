@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.app

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.missioncontrol.ui.GlobalView
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.MissionControlIntent
import dev.quayside.missioncontrol.ui.SessionFilter
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectInspector
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.projects.ui.DirectoryPicker
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsPorts
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/**
 * `onStarted` of the "Nowy agent" dialog (AM-608, rule 6): the sidebar selects the project, then Mission Control
 * selects the new session. Session `fresh` is a working session of `Quayside` that was just started.
 */
class SelectStartedSessionTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private val fresh = SessionId("fresh001")
    private val fake =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(project.id, project.dir)),
                sessions =
                    listOf(
                        WorldSession(fresh, A_PROJECT_DIR, SessionState.Working, "fresh", T0.plusSeconds(10)),
                        WorldSession(SessionId("old00001"), A_PROJECT_DIR, SessionState.Done, "old", T0),
                    ),
            ),
            TestClock(T0),
        )

    private class Parts(
        val projects: ProjectsController,
        val missionControl: MissionControlController,
    )

    private fun TestScope.parts(): Parts {
        val api = SessionsApi(monitor = fake, commands = fake)
        val list = FakeProjectsApi(initial = listOf(project))
        val projects =
            ProjectsController(
                list,
                FakeProjectInspector(projects = list),
                api,
                ProjectsPorts(DirectoryPicker { null }, NoProjectHooks),
                backgroundScope,
                NoOpLogger,
            )
        val missionControl =
            MissionControlController(api, list, projects.selection, TestClock(T0), backgroundScope, NoOpLogger, 1.hours)
        runCurrent()
        return Parts(projects, missionControl)
    }

    @Test
    fun `the sidebar selects the project of the new session`() =
        runTest {
            val parts = parts()
            parts.projects.selection.value
                .shouldBeNull()

            selectStartedSession(parts.projects, parts.missionControl)(project.id, fresh)
            runCurrent()

            parts.projects.selection.value shouldBe project.id
        }

    @Test
    fun `Mission Control selects the new session`() =
        runTest {
            val parts = parts()

            selectStartedSession(parts.projects, parts.missionControl)(project.id, fresh)
            runCurrent()

            // The fresh session is working, so it can be stopped; the old one could only be respawned.
            parts.missionControl.actions.value.canStop shouldBe true
            parts.missionControl.actions.value.canRespawn shouldBe false
            parts.missionControl.attachTarget()?.session shouldBe fresh
        }

    @Test
    fun `the selection survives the sidebar catching up`() =
        runTest {
            val parts = parts()

            selectStartedSession(parts.projects, parts.missionControl)(project.id, fresh)
            runCurrent()
            runCurrent()

            parts.missionControl.actions.value.canStop shouldBe true
            parts.missionControl.attachTarget()?.session shouldBe fresh
        }

    @Test
    fun `a global view is left and the filter is all`() =
        runTest {
            val parts = parts()
            parts.missionControl.onIntent(MissionControlIntent.ShowView(GlobalView.WAITING))
            parts.missionControl.onIntent(MissionControlIntent.SelectFilter(SessionFilter.FINISHED))
            runCurrent()
            parts.missionControl.view.value shouldBe GlobalView.WAITING

            selectStartedSession(parts.projects, parts.missionControl)(project.id, fresh)
            runCurrent()

            parts.missionControl.view.value
                .shouldBeNull()
            parts.missionControl.actions.value.canStop shouldBe true
        }
}
