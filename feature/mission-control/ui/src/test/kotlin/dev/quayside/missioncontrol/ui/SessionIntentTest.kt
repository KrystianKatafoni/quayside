@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.ProjectId
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
 * `onIntent` for j and k (FR-K2, AM-409). Sessions start at T0 + their index, so the newest is the first row
 * (the reducer sorts by start, newest first): the rows below are c, b, a.
 */
internal class SessionIntentTest {
    private val project = aProject(displayName = "Quayside")

    private inner class World(
        sessions: List<WorldSession> = emptyList(),
        selected: ProjectId? = project.id,
    ) {
        val clock = TestClock()
        val projects = FakeProjectsApi(initial = listOf(project))
        val sessions =
            FakeSessions(SessionsWorld(listOf(WorldProject(project.id, project.dir)), sessions), clock)
        val selected = MutableStateFlow(selected)

        fun TestScope.start(): MissionControlController =
            MissionControlController(sessions.api(), projects, selected, clock, backgroundScope, NoOpLogger, 1.hours)
                .also { runCurrent() }
    }

    private fun session(
        id: String,
        index: Long,
        state: SessionState = SessionState.Working,
    ): WorldSession =
        WorldSession(id = SessionId(id), cwd = A_PROJECT_DIR, state = state, startedAt = T0.plusSeconds(index))

    private val three = listOf(session("a", 1), session("b", 2), session("c", 3))

    private fun MissionControlController.page(): Screen.Project =
        state.value.screen.shouldBeInstanceOf<Screen.Project>()

    private fun MissionControlController.rows(): List<String> = page().rows.map { it.id.value }

    private fun MissionControlController.selectedRow(): String? =
        page()
            .rows
            .singleOrNull { it.selected }
            ?.id
            ?.value

    private fun TestScope.send(
        controller: MissionControlController,
        vararg intents: MissionControlIntent,
    ) {
        intents.forEach {
            controller.onIntent(it)
            runCurrent()
        }
    }

    @Test
    fun `next session without a selection selects the first row`() =
        runTest {
            val controller = with(World(three)) { start() }
            controller.rows() shouldBe listOf("c", "b", "a")

            send(controller, MissionControlIntent.NextSession)

            controller.selectedRow() shouldBe "c"
            controller.page().details?.id shouldBe SessionId("c")
        }

    @Test
    fun `previous session without a selection selects the last row`() =
        runTest {
            val controller = with(World(three)) { start() }

            send(controller, MissionControlIntent.PreviousSession)

            controller.selectedRow() shouldBe "a"
            controller.page().details?.id shouldBe SessionId("a")
        }

    @Test
    fun `next and previous move one row at a time`() =
        runTest {
            val controller = with(World(three)) { start() }

            send(controller, MissionControlIntent.NextSession, MissionControlIntent.NextSession)
            controller.selectedRow() shouldBe "b"

            send(controller, MissionControlIntent.PreviousSession)
            controller.selectedRow() shouldBe "c"
        }

    @Test
    fun `next session on the last row stays`() =
        runTest {
            val controller = with(World(three)) { start() }

            send(controller, MissionControlIntent.PreviousSession, MissionControlIntent.NextSession)

            controller.selectedRow() shouldBe "a"
        }

    @Test
    fun `previous session on the first row stays`() =
        runTest {
            val controller = with(World(three)) { start() }

            send(controller, MissionControlIntent.NextSession, MissionControlIntent.PreviousSession)

            controller.selectedRow() shouldBe "c"
        }

    @Test
    fun `intents without rows change nothing`() =
        runTest {
            val world = World()
            val controller = with(world) { start() }

            send(controller, MissionControlIntent.NextSession, MissionControlIntent.PreviousSession)

            controller.rows().shouldBeEmpty()
            controller.page().details.shouldBeNull()

            // A session that appears later is not selected by the earlier keys.
            world.sessions.appear(session("late", 5))
            runCurrent()
            controller.selectedRow().shouldBeNull()
        }

    @Test
    fun `intents without a shown project change nothing`() =
        runTest {
            val world = World(three, selected = null)
            val controller = with(world) { start() }
            val before = controller.state.value

            send(controller, MissionControlIntent.NextSession, MissionControlIntent.PreviousSession)

            controller.state.value shouldBe before
            world.selected.value = project.id
            runCurrent()
            controller.selectedRow().shouldBeNull()
        }

    @Test
    fun `next session skips rows hidden by the filter`() =
        runTest {
            val world = World(listOf(session("a", 1), session("b", 2, SessionState.Done), session("c", 3)))
            val controller = with(world) { start() }
            controller.onEvent(MissionControlEvent.SelectFilter(SessionFilter.ACTIVE))
            controller.onEvent(MissionControlEvent.SelectSession(SessionId("c")))
            runCurrent()
            controller.rows() shouldBe listOf("c", "a")

            send(controller, MissionControlIntent.NextSession)

            controller.selectedRow() shouldBe "a"
        }

    @Test
    fun `a selection hidden by the filter counts as no selection`() =
        runTest {
            val world = World(listOf(session("a", 1), session("b", 2, SessionState.Done), session("c", 3)))
            val controller = with(world) { start() }
            controller.onEvent(MissionControlEvent.SelectSession(SessionId("b")))
            controller.onEvent(MissionControlEvent.SelectFilter(SessionFilter.ACTIVE))
            runCurrent()
            controller.selectedRow().shouldBeNull()

            send(controller, MissionControlIntent.NextSession)

            controller.selectedRow() shouldBe "c"
        }

    @Test
    fun `two quick next intents move two rows`() =
        runTest {
            val controller = with(World(three)) { start() }

            controller.onIntent(MissionControlIntent.NextSession)
            controller.onIntent(MissionControlIntent.NextSession)
            runCurrent()

            controller.selectedRow() shouldBe "b"
        }
}
