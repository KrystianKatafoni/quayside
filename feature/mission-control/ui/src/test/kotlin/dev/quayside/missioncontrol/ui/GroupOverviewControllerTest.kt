@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/** The group overview in the controller (AM-307): the page of the group, and a click that waits for the sidebar. */
internal class GroupOverviewControllerTest {
    private val selected = SelectedProject()

    private fun world(
        id: String,
        cwd: String,
        state: SessionState,
        index: Long,
    ) = WorldSession(
        SessionId(id),
        OVERVIEW_DIR.resolve(cwd),
        state,
        name = "name-$id",
        startedAt = T0.plusSeconds(index),
    )

    private fun TestScope.start(): MissionControlController {
        selected.focus.value = ProjectFocus(OVERVIEW_GROUP.id)
        val fake =
            FakeSessions(
                SessionsWorld(
                    projects = listOf(WorldProject(OVERVIEW_GROUP.id, OVERVIEW_GROUP.dir)),
                    sessions =
                        listOf(
                            world("ask", "Carl", SessionState.AwaitingInput("q"), 0),
                            world("busy", "Anna/api", SessionState.Working, 1),
                        ),
                ),
                TestClock(T0),
            )
        return MissionControlController(
            fake.api(),
            FakeProjectsApi(initial = listOf(OVERVIEW_GROUP)),
            selected.focus,
            TestClock(T0),
            backgroundScope,
            NoOpLogger,
            1.hours,
        ).also { runCurrent() }
    }

    private fun MissionControlController.page(): Screen.Project =
        state.value.screen.shouldBeInstanceOf<Screen.Project>()

    @Test
    fun `group page carries the overview of the live snapshot`() =
        runTest {
            val controller = start()

            controller
                .page()
                .overview
                .shouldNotBeNull()
                .rows
                .map { it.name.value to it.stats.counts() } shouldBe
                listOf("Carl" to Counts(waiting = 1), "Anna" to Counts(working = 1), "bob" to Counts())
        }

    @Test
    fun `open subfolder event leaves the selection to the sidebar`() =
        runTest {
            // Given the group page
            val controller = start()
            val before = controller.state.value

            // When the overview reports a click on a subfolder row
            controller.onEvent(MissionControlEvent.OpenSubfolder(ProjectFocus(OVERVIEW_GROUP.id, sub("Carl"))))
            runCurrent()

            // Then nothing changes until the sidebar reports the new focus
            controller.state.value shouldBe before
            controller.page().overview.shouldNotBeNull()

            // And when it does, the page of the subfolder replaces the overview
            selected.focus.value = ProjectFocus(OVERVIEW_GROUP.id, sub("Carl"))
            runCurrent()
            controller.page().header.name shouldBe "plant-puls / Carl"
            controller.page().overview.shouldBeNull()
        }
}
