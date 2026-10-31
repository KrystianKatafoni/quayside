@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
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
 * `SelectFilter` intent from the palette (FR-S9, AM-409 question 1). Kept apart so it can move to AM-410
 * without touching the j/k tests.
 */
internal class FilterCommandsTest {
    private val project = aProject()
    private val clock = TestClock()

    private fun TestScope.controller(): MissionControlController {
        val sessions =
            FakeSessions(
                SessionsWorld(
                    listOf(WorldProject(project.id, project.dir)),
                    listOf(
                        WorldSession(SessionId("working"), A_PROJECT_DIR, SessionState.Working, startedAt = T0),
                        WorldSession(SessionId("done"), A_PROJECT_DIR, SessionState.Done, startedAt = T0),
                    ),
                ),
                clock,
            )
        return MissionControlController(
            sessions.api(),
            FakeProjectsApi(initial = listOf(project)),
            MutableStateFlow(project.id),
            clock,
            backgroundScope,
            NoOpLogger,
            1.hours,
        ).also { runCurrent() }
    }

    private fun MissionControlController.rows(): List<String> =
        state.value.screen
            .shouldBeInstanceOf<Screen.Project>()
            .rows
            .map { it.id.value }
            .sorted()

    @Test
    fun `select filter intent changes the list`() =
        runTest {
            val controller = controller()

            controller.onIntent(MissionControlIntent.SelectFilter(SessionFilter.FINISHED))
            runCurrent()
            controller.rows() shouldBe listOf("done")

            controller.onIntent(MissionControlIntent.SelectFilter(SessionFilter.ACTIVE))
            runCurrent()
            controller.rows() shouldBe listOf("working")

            controller.onIntent(MissionControlIntent.SelectFilter(SessionFilter.ALL))
            runCurrent()
            controller.rows() shouldBe listOf("done", "working")
        }
}
