package dev.quayside.projects.ui

import dev.quayside.projects.api.DirEntry
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.PathKind
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.TestScope
import java.nio.file.Path

/**
 * The group of the subfolders dialog tests (AM-306): `plant-puls` stores `bob`; on its disk are `bob`, `Anna` and
 * `Carl`, and `Carl` is also a registered project of its own. Names differ from every fixed text of the UI.
 */
internal object SubfolderFixture {
    val plantDir: Path = Path.of("/Users/test/projects/plant-puls")
    val plant = aProject(id = "project0001", dir = plantDir, displayName = "Plant", subfolders = subfolderNames("bob"))
    val other = aProject(id = "project0002", dir = Path.of("/Users/test/projects/other"), displayName = "Other")
    val carlProject = aProject(id = "project0003", dir = plantDir.resolve("Carl"), displayName = "Carl project")
    val bob = SubfolderName("bob")
    val anna = SubfolderName("Anna")
    val carl = SubfolderName("Carl")

    private val disk: (Path) -> List<DirEntry> = { dir ->
        when (dir) {
            plantDir -> listOf(DirEntry("bob"), DirEntry("Anna"), DirEntry("Carl"))
            other.dir -> listOf(DirEntry("api"))
            else -> emptyList()
        }
    }

    fun world(
        projects: List<Project> = listOf(plant, other, carlProject),
        pathKind: (Path) -> PathKind = { PathKind.DIRECTORY },
        gated: Boolean = false,
    ): ControllerWorld =
        ControllerWorld(
            projects = FakeProjectsApi(initial = projects),
            pathKind = pathKind,
            gatedInspection = gated,
        ).also { it.disk = disk }
}

/** Asks for the subfolders dialog of [project] the way the row menu does. */
internal fun TestScope.openSubfolders(
    controller: ProjectsController,
    project: Project = SubfolderFixture.plant,
) {
    act { controller.onEvent(SidebarEvent.EditSubfoldersRequested(project.id)) }
}

/** The open subfolders dialog; fails the test when another dialog or none is open. */
internal fun ProjectsController.subfoldersDialog(): Dialog.Subfolders =
    state.value.dialog.shouldBeInstanceOf<Dialog.Subfolders>()
