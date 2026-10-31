@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.app

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.MissionControlIntent
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/** The worktree of the session selected in Mission Control, as the shell start reads it (FR-T7, AM-508). */
class SelectedSessionDirTest {
    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private val worktree = A_PROJECT_DIR.resolve(".claude/worktrees/fix-login")
    private val fake =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(project.id, project.dir)),
                sessions = listOf(WorldSession(id = SessionId("s1"), cwd = worktree, startedAt = T0)),
            ),
            TestClock(T0),
        )

    private fun TestScope.missionControl(): MissionControlController =
        MissionControlController(
            SessionsApi(monitor = fake, commands = fake),
            FakeProjectsApi(initial = listOf(project)),
            MutableStateFlow(project.id),
            TestClock(T0),
            backgroundScope,
            NoOpLogger,
            1.hours,
        ).also { runCurrent() }

    @Test
    fun `cwd of the selected session of the project`() =
        runTest {
            val missionControl = missionControl()
            missionControl.onIntent(MissionControlIntent.NextSession)

            selectedSessionDirOf(missionControl, fake.snapshot)(project.id) shouldBe worktree
        }

    @Test
    fun `null for another project`() =
        runTest {
            val missionControl = missionControl()
            missionControl.onIntent(MissionControlIntent.NextSession)

            selectedSessionDirOf(missionControl, fake.snapshot)(ProjectId("project0002")) shouldBe null
        }

    @Test
    fun `null without a selected session`() =
        runTest {
            val missionControl = missionControl()

            selectedSessionDirOf(missionControl, fake.snapshot)(project.id) shouldBe null
        }
}
