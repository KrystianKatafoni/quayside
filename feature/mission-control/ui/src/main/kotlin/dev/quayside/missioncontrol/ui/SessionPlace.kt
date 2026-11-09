package dev.quayside.missioncontrol.ui

import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectGroups
import java.nio.file.Path

/**
 * Where a session of a group runs, for the group page only (FR-P7, FR-S9): the subfolder when [cwd] is in a checked
 * one (`row.place.subfolder`, argument = the name), else the main folder (`row.place.root`: the project directory, an
 * unchecked subdirectory, a path outside the project). Null for a project without subfolders: no badge.
 */
internal object SessionPlace {
    fun of(
        project: Project,
        cwd: Path,
    ): UiText? {
        if (project.subfolders.isEmpty()) return null
        val subfolder = ProjectGroups.subfolderOf(project, cwd)
        return subfolder?.let { UiText(SUBFOLDER_KEY, listOf(it.value)) } ?: UiText("row.place.root")
    }

    /** Key of the subfolder text; the badge shows just its argument. */
    const val SUBFOLDER_KEY = "row.place.subfolder"
}
