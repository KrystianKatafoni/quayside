@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

/**
 * The project page narrowed to one subfolder of a group (FR-P7, FR-S9, AM-306) on fakes: the controller follows
 * `ProjectsController.focus`, which the test sets directly. Names (`plant-puls`, `soft`) differ from fixed texts.
 */
internal class SubfolderPageControllerTest {
    private val plantDir = Path.of("/Users/test/projects/plant-puls")
    private val plant =
        aProject(
            id = "plant",
            dir = plantDir,
            displayName = "plant-puls",
            subfolders = subfolderNames("docs", "soft"),
        )
    private val inner = aProject(id = "inner", dir = plantDir.resolve("soft/inner"), displayName = "inner")
    private val other = aProject(id = "other", dir = Path.of("/Users/test/projects/other"), displayName = "other")
    private val soft = SubfolderName("soft")
    private val docs = SubfolderName("docs")

    private fun world(
        id: String,
        cwd: Path,
        state: SessionState,
        index: Long,
    ) = WorldSession(SessionId(id), cwd, state, name = "name-$id", startedAt = T0.plusSeconds(index))

    private val running =
        listOf(
            world("root", plantDir, SessionState.Working, 0),
            world("soft-work", plantDir.resolve("soft/api"), SessionState.Working, 1),
            world("soft-done", plantDir.resolve("soft"), SessionState.Done, 2),
            world("soft-ask", plantDir.resolve("soft"), SessionState.AwaitingInput("q"), 3),
            world("docs-work", plantDir.resolve("docs"), SessionState.Working, 4),
            world("marketing", plantDir.resolve("marketing"), SessionState.Working, 5),
            world("nested", plantDir.resolve("soft/inner/x"), SessionState.Working, 6),
            world("theirs", other.dir, SessionState.Working, 7),
        )

    private val clock = TestClock()
    private val selected = SelectedProject()

    private fun TestScope.start(focus: ProjectFocus? = ProjectFocus(plant.id, soft)): MissionControlController {
        selected.focus.value = focus
        val fake =
            FakeSessions(
                SessionsWorld(
                    projects = listOf(plant, inner, other).map { WorldProject(it.id, it.dir) },
                    sessions = running,
                ),
                clock,
            )
        return MissionControlController(
            fake.api(),
            FakeProjectsApi(initial = listOf(plant, inner, other)),
            selected.focus,
            clock,
            backgroundScope,
            NoOpLogger,
            1.hours,
        ).also { runCurrent() }
    }

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
    fun `subfolder page shows only its sessions`() =
        runTest {
            val controller = start()

            controller.rows() shouldBe listOf("soft-ask", "soft-done", "soft-work")
        }

    @Test
    fun `filter tabs apply inside the subfolder`() =
        runTest {
            val controller = start()

            send(controller, MissionControlIntent.SelectFilter(SessionFilter.ACTIVE))
            controller.rows() shouldBe listOf("soft-ask", "soft-work")

            send(controller, MissionControlIntent.SelectFilter(SessionFilter.FINISHED))
            controller.rows() shouldBe listOf("soft-done")

            send(controller, MissionControlIntent.SelectFilter(SessionFilter.ALL))
            controller.rows() shouldBe listOf("soft-ask", "soft-done", "soft-work")
        }

    @Test
    fun `filter by click applies inside the subfolder`() =
        runTest {
            val controller = start()

            controller.onEvent(MissionControlEvent.SelectFilter(SessionFilter.FINISHED))
            runCurrent()

            controller.rows() shouldBe listOf("soft-done")
        }

    @Test
    fun `header shows project and subfolder name, path and count`() =
        runTest {
            val controller = start()

            controller.page().header shouldBe ProjectHeader("plant-puls / soft", plantDir.resolve("soft"), sessions = 3)
        }

    @Test
    fun `header count and stat cards ignore the filter but stay inside the subfolder`() =
        runTest {
            val controller = start()

            send(controller, MissionControlIntent.SelectFilter(SessionFilter.FINISHED))

            controller.page().header.sessions shouldBe 3
            controller.page().stats shouldBe SessionStats(waiting = 1, working = 1, done = 1, failed = 0)
        }

    @Test
    fun `mode bar and crumbs name the project and the subfolder`() =
        runTest {
            val controller = start()

            controller.state.value.modeBar.project shouldBe "plant-puls / soft"
            controller.names.value.project shouldBe "plant-puls / soft"
        }

    @Test
    fun `mode bar counts stay over every session`() =
        runTest {
            val controller = start()

            controller.state.value.modeBar.working shouldBe 6
            controller.state.value.modeBar.waiting shouldBe 1
        }

    @Test
    fun `j and k move only over subfolder rows`() =
        runTest {
            val controller = start()

            send(controller, MissionControlIntent.NextSession)
            controller.selectedRow() shouldBe "soft-ask"
            send(controller, MissionControlIntent.NextSession, MissionControlIntent.NextSession)
            controller.selectedRow() shouldBe "soft-work"
            send(controller, MissionControlIntent.NextSession)
            controller.selectedRow() shouldBe "soft-work"
            send(controller, MissionControlIntent.PreviousSession)
            controller.selectedRow() shouldBe "soft-done"
        }

    @Test
    fun `stop acts on the selected session of the subfolder`() =
        runTest {
            val controller = start()
            send(controller, MissionControlIntent.NextSession)

            controller.actions.value.canStop shouldBe true
            controller.actions.value.canRespawn shouldBe false
        }

    @Test
    fun `the project row shows the whole project again`() =
        runTest {
            val controller = start()

            selected.focus.value = ProjectFocus(plant.id)
            runCurrent()

            controller.rows() shouldBe listOf("marketing", "docs-work", "soft-ask", "soft-done", "soft-work", "root")
            controller.page().header shouldBe ProjectHeader("plant-puls", plantDir, sessions = 6)
            controller.state.value.modeBar.project shouldBe "plant-puls"
        }

    @Test
    fun `other subfolder starts a fresh page`() =
        runTest {
            val controller = start()
            send(
                controller,
                MissionControlIntent.SelectFilter(SessionFilter.FINISHED),
                MissionControlIntent.NextSession,
            )
            controller.selectedRow() shouldBe "soft-done"

            selected.focus.value = ProjectFocus(plant.id, docs)
            runCurrent()

            controller.page().filter shouldBe SessionFilter.ALL
            controller.rows() shouldBe listOf("docs-work")
            controller.selectedRow() shouldBe null
            controller.page().header.name shouldBe "plant-puls / docs"
        }

    @Test
    fun `same focus keeps the selected session`() =
        runTest {
            // Given the soft page, from which a popup attaches to a session of the project directory
            val controller = start()

            // When app selects the session on the project page and the sidebar then follows with the project
            controller.onIntent(MissionControlIntent.SelectSession(plant.id, SessionId("root")))
            runCurrent()
            selected.focus.value = ProjectFocus(plant.id)
            runCurrent()

            // Then the project page keeps that session selected, with filter all
            controller.page().header.name shouldBe "plant-puls"
            controller.page().filter shouldBe SessionFilter.ALL
            controller.selectedRow() shouldBe "root"
            controller.page().details?.id shouldBe SessionId("root")
        }

    @Test
    fun `select session on the subfolder page leaves the subfolder at once`() =
        runTest {
            val controller = start()

            controller.onIntent(MissionControlIntent.SelectSession(plant.id, SessionId("marketing")))
            runCurrent()

            controller.page().header.name shouldBe "plant-puls"
            controller.selectedRow() shouldBe "marketing"
        }

    @Test
    fun `a session in the subfolder can be selected from the sidebar order too`() =
        runTest {
            // The sidebar first reports the project, then app selects the session (new agent in a subfolder page).
            val controller = start()

            selected.focus.value = ProjectFocus(plant.id)
            runCurrent()
            controller.onIntent(MissionControlIntent.SelectSession(plant.id, SessionId("soft-work")))
            runCurrent()

            controller.page().header.name shouldBe "plant-puls"
            controller.selectedRow() shouldBe "soft-work"
        }

    @Test
    fun `another project replaces the subfolder page`() =
        runTest {
            val controller = start()

            selected.focus.value = ProjectFocus(other.id)
            runCurrent()

            controller.page().header.name shouldBe "other"
            controller.rows() shouldBe listOf("theirs")
        }

    @Test
    fun `a subfolder focus at the start shows the subfolder page at once`() =
        runTest {
            val controller = start(ProjectFocus(plant.id, docs))

            controller.rows() shouldBe listOf("docs-work")
        }
}
