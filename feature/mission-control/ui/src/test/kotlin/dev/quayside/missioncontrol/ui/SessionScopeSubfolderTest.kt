package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Which sessions a subfolder page shows (FR-P7, FR-S9, AM-306): the sessions of the group project whose directory is
 * in the subfolder, under the filter. Sessions of a nested project already belong to that project.
 */
internal class SessionScopeSubfolderTest {
    private val plant =
        aProject(
            id = "plant",
            dir = Path.of("/p/plant-puls"),
            displayName = "plant-puls",
            subfolders = subfolderNames("docs", "soft"),
        )
    private val inner = aProject(id = "inner", dir = Path.of("/p/plant-puls/soft/inner"), displayName = "inner")
    private val projects = listOf(plant, inner)
    private val soft = SubfolderName("soft")

    private val sessions: List<Session> =
        listOf(
            session("root", "/p/plant-puls", SessionState.Working, 0),
            session("soft-work", "/p/plant-puls/soft/api", SessionState.Working, 1),
            session("soft-done", "/p/plant-puls/soft", SessionState.Done, 2),
            session("soft-ask", "/p/plant-puls/soft", SessionState.AwaitingInput("q"), 3),
            session("docs-work", "/p/plant-puls/docs", SessionState.Working, 4),
            session("marketing", "/p/plant-puls/marketing", SessionState.Working, 5),
            session("sibling", "/p/plant-puls/software", SessionState.Working, 6),
            session("nested", "/p/plant-puls/soft/inner/x", SessionState.Working, 7, project = inner.id),
            session("other", "/p/other", SessionState.Working, 8, project = ProjectId("other")),
        )

    private fun session(
        id: String,
        cwd: String,
        state: SessionState,
        index: Long,
        project: ProjectId = plant.id,
    ) = aSession(id = id, project = project, cwd = Path.of(cwd), state = state, startedAt = T0.plusSeconds(index))

    private fun visible(selection: Selection): List<String> =
        SessionScope.visible(sessions, selection, projects).map { it.id.value }

    @Test
    fun `subfolder page shows only its sessions`() {
        visible(Selection(project = plant.id, subfolder = soft)) shouldBe listOf("soft-ask", "soft-done", "soft-work")
    }

    @Test
    fun `filter applies inside the subfolder`() {
        visible(Selection(project = plant.id, subfolder = soft, filter = SessionFilter.ACTIVE)) shouldBe
            listOf("soft-ask", "soft-work")
        visible(Selection(project = plant.id, subfolder = soft, filter = SessionFilter.FINISHED)) shouldBe
            listOf("soft-done")
    }

    @Test
    fun `project page without a subfolder still shows every session of the project`() {
        visible(Selection(project = plant.id)) shouldBe
            listOf("sibling", "marketing", "docs-work", "soft-ask", "soft-done", "soft-work", "root")
    }

    @Test
    fun `session of a nested project is not on the subfolder page`() {
        visible(Selection(project = plant.id, subfolder = soft)) shouldBe listOf("soft-ask", "soft-done", "soft-work")
        visible(Selection(project = inner.id)) shouldBe listOf("nested")
    }

    @Test
    fun `a subfolder that is not in the group shows nothing`() {
        visible(Selection(project = plant.id, subfolder = SubfolderName("marketing"))) shouldBe emptyList()
    }

    @Test
    fun `without the projects a subfolder page shows nothing`() {
        SessionScope.visible(sessions, Selection(project = plant.id, subfolder = soft), emptyList()) shouldBe
            emptyList()
    }

    @Test
    fun `a subfolder is ignored by a global view`() {
        visible(Selection(view = GlobalView.WORKING, project = plant.id, subfolder = soft)) shouldBe
            visible(Selection(view = GlobalView.WORKING))
    }
}
