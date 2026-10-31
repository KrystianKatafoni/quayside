@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Clock
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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The controller on fakes (FakeSessions, FakeProjectsApi, TestClock) in `backgroundScope`. Time moves only by
 * `advanceTimeBy` + `runCurrent`, never `advanceUntilIdle`: the tick loop never ends.
 */
internal class MissionControlControllerTest {
    private val project = aProject(displayName = "Quayside")
    private val other = aProject(id = "other", dir = Path.of("/Users/test/projects/other"), displayName = "Other")

    private inner class World(
        sessions: List<WorldSession> = emptyList(),
        selected: ProjectId? = project.id,
    ) {
        val clock = TestClock()
        val projects = FakeProjectsApi(initial = listOf(project, other))
        val sessions =
            FakeSessions(
                SessionsWorld(
                    projects = listOf(WorldProject(project.id, project.dir), WorldProject(other.id, other.dir)),
                    sessions = sessions,
                ),
                clock,
            )
        val selected = MutableStateFlow(selected)

        fun TestScope.start(tick: Duration = 1.seconds): MissionControlController =
            MissionControlController(
                sessions.api(),
                projects,
                selected,
                clock,
                backgroundScope,
                NoOpLogger,
                tick,
            ).also {
                runCurrent()
            }
    }

    private fun session(
        id: String,
        state: SessionState = SessionState.Working,
        cwd: Path = A_PROJECT_DIR,
    ): WorldSession = WorldSession(id = SessionId(id), cwd = cwd, state = state, startedAt = T0)

    private fun MissionControlController.page(): Screen.Project =
        state.value.screen.shouldBeInstanceOf<Screen.Project>()

    private fun MissionControlController.age(): UiText = page().rows.single().age

    @Test
    fun `starts with the sessions of the selected project`() =
        runTest {
            val world = World(sessions = listOf(session("mine"), session("theirs", cwd = other.dir)))
            val controller = with(world) { start() }

            controller.page().rows.map { it.id.value } shouldBe listOf("mine")
            controller.page().header.name shouldBe "Quayside"
        }

    @Test
    fun `new snapshot updates the rows`() =
        runTest {
            val world = World(sessions = listOf(session("first")))
            val controller = with(world) { start() }

            world.sessions.appear(session("second"))
            runCurrent()

            controller
                .page()
                .rows
                .map { it.id.value }
                .toSet() shouldBe setOf("first", "second")

            world.sessions.setState(SessionId("first"), SessionState.AwaitingInput("q"))
            runCurrent()

            controller
                .page()
                .rows
                .single { it.id.value == "first" }
                .glyph.symbol shouldBe "?"
            controller.state.value.modeBar.waiting shouldBe 1
        }

    @Test
    fun `ages advance with the clock every tick`() =
        runTest {
            val world = World(sessions = listOf(session("aging")))
            val controller = with(world) { start() }
            controller.age() shouldBe UiText("age.now")

            world.clock.advanceBy(2.minutes)
            runCurrent()
            // The clock is read on the tick, not on every frame.
            controller.age() shouldBe UiText("age.now")

            advanceTimeBy(1.seconds)
            runCurrent()
            controller.age() shouldBe UiText("age.min", listOf("2"))

            world.clock.advanceBy(1.minutes)
            advanceTimeBy(1.seconds)
            runCurrent()
            controller.age() shouldBe UiText("age.min", listOf("3"))
        }

    @Test
    fun `ages do not tick without a selected project`() =
        runTest {
            val world = World(sessions = listOf(session("aging")), selected = null)
            var reads = 0
            val counting = Clock { world.clock.now().also { reads++ } }
            MissionControlController(
                world.sessions.api(),
                world.projects,
                world.selected,
                counting,
                backgroundScope,
                NoOpLogger,
            )
            runCurrent()
            val atStart = reads

            world.clock.advanceBy(2.minutes)
            advanceTimeBy(10.seconds)
            runCurrent()
            reads shouldBe atStart

            // Selecting a project reads the clock at once, without waiting for the first tick.
            world.selected.value = project.id
            runCurrent()
            (reads > atStart) shouldBe true
        }

    @Test
    fun `selecting a project shows the current age at once`() =
        runTest {
            val world = World(sessions = listOf(session("aging")), selected = null)
            val controller = with(world) { start(tick = 1.minutes) }

            world.clock.advanceBy(2.minutes)
            world.selected.value = project.id
            runCurrent()

            controller.age() shouldBe UiText("age.min", listOf("2"))
        }

    @Test
    fun `clicking a filter and a session updates the screen`() =
        runTest {
            val world = World(sessions = listOf(session("working"), session("done", state = SessionState.Done)))
            val controller = with(world) { start() }

            controller.onEvent(MissionControlEvent.SelectSession(SessionId("working")))
            runCurrent()
            controller.page().details?.id shouldBe SessionId("working")

            controller.onEvent(MissionControlEvent.SelectFilter(SessionFilter.FINISHED))
            runCurrent()
            controller.page().filter shouldBe SessionFilter.FINISHED
            controller.page().rows.map { it.id.value } shouldBe listOf("done")
            controller.page().details.shouldBeNull()
        }

    @Test
    fun `changing the project resets filter and selection`() =
        runTest {
            val world = World(sessions = listOf(session("mine"), session("theirs", cwd = other.dir)))
            val controller = with(world) { start() }
            controller.onEvent(MissionControlEvent.SelectFilter(SessionFilter.ACTIVE))
            controller.onEvent(MissionControlEvent.SelectSession(SessionId("mine")))
            runCurrent()

            world.selected.value = other.id
            runCurrent()

            val page = controller.page()
            page.header.name shouldBe "Other"
            page.filter shouldBe SessionFilter.ALL
            page.details.shouldBeNull()
            page.rows.map { it.id.value } shouldBe listOf("theirs")

            // Back to the first project: the earlier selection is not restored.
            world.selected.value = project.id
            runCurrent()
            controller.page().details.shouldBeNull()
            controller.page().filter shouldBe SessionFilter.ALL
        }

    @Test
    fun `no selected project asks to pick one`() =
        runTest {
            val world = World(sessions = listOf(session("mine")), selected = null)
            val controller = with(world) { start() }

            controller.state.value.screen shouldBe Screen.NoSelection
            controller.state.value.modeBar.working shouldBe 1
        }

    @Test
    fun `removed project falls back to the empty screen`() =
        runTest {
            val world = World(sessions = listOf(session("mine")))
            val controller = with(world) { start() }

            world.projects.remove(other.id)
            world.projects.remove(project.id)
            runCurrent()

            controller.state.value.screen shouldBe Screen.NoProjects
        }

    @Test
    fun `non positive tick is rejected`() =
        runTest {
            val world = World()

            shouldThrow<IllegalArgumentException> { with(world) { start(tick = Duration.ZERO) } }
            shouldThrow<IllegalArgumentException> { with(world) { start(tick = (-1).seconds) } }
        }
}
