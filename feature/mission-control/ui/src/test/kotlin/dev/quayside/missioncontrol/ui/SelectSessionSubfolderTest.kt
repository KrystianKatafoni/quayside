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
 * `SelectSession` with a subfolder: `app` selects the session it has just started in a subfolder of a group
 * (AM-308 plan, rule 5). The sidebar focus is set by the test, as `app` does with `ProjectsIntent.Select`.
 */
internal class SelectSessionSubfolderTest {
    private val dir = Path.of("/Users/test/projects/plant-puls")
    private val plant = aProject("plant", dir, "plant-puls", subfolderNames("docs", "soft"))
    private val soft = SubfolderName("soft")
    private val clock = TestClock()
    private val selected = SelectedProject()

    private fun session(
        id: String,
        cwd: Path,
        index: Long,
    ) = WorldSession(SessionId(id), cwd, SessionState.Working, name = "name-$id", startedAt = T0.plusSeconds(index))

    private fun TestScope.start(focus: ProjectFocus?): MissionControlController {
        selected.focus.value = focus
        val sessions =
            listOf(
                session("root", dir, 0),
                session("soft-new", dir.resolve("soft"), 1),
                session("docs-old", dir.resolve("docs"), 2),
            )
        val fake = FakeSessions(SessionsWorld(listOf(WorldProject(plant.id, plant.dir)), sessions), clock)
        return MissionControlController(
            fake.api(),
            FakeProjectsApi(initial = listOf(plant)),
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

    @Test
    fun `select session with a subfolder opens the subfolder page with the session`() =
        runTest {
            val controller = start(ProjectFocus(plant.id))

            controller.onIntent(MissionControlIntent.SelectSession(plant.id, SessionId("soft-new"), soft))
            runCurrent()

            controller.page().header.name shouldBe "plant-puls / soft"
            controller.rows() shouldBe listOf("soft-new")
            controller.selectedRow() shouldBe "soft-new"
            controller.page().filter shouldBe SessionFilter.ALL
        }

    @Test
    fun `the sidebar following with the same subfolder keeps the session`() =
        runTest {
            val controller = start(ProjectFocus(plant.id, SubfolderName("docs")))

            controller.onIntent(MissionControlIntent.SelectSession(plant.id, SessionId("soft-new"), soft))
            runCurrent()
            selected.focus.value = ProjectFocus(plant.id, soft)
            runCurrent()

            controller.page().header.name shouldBe "plant-puls / soft"
            controller.selectedRow() shouldBe "soft-new"
        }

    @Test
    fun `select session without a subfolder leaves the subfolder page`() =
        runTest {
            val controller = start(ProjectFocus(plant.id, soft))

            controller.onIntent(MissionControlIntent.SelectSession(plant.id, SessionId("root")))
            runCurrent()

            controller.page().header.name shouldBe "plant-puls"
            controller.selectedRow() shouldBe "root"
        }

    @Test
    fun `select session with a subfolder of another group row shows that subfolder`() =
        runTest {
            val controller = start(ProjectFocus(plant.id, soft))

            controller.onIntent(
                MissionControlIntent.SelectSession(plant.id, SessionId("docs-old"), SubfolderName("docs")),
            )
            runCurrent()

            controller.rows() shouldBe listOf("docs-old")
            controller.page().header.name shouldBe "plant-puls / docs"
        }
}
