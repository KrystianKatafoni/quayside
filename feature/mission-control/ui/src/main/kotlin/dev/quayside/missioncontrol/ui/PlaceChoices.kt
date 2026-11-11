package dev.quayside.missioncontrol.ui

import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectGroups
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.Session

/**
 * One line of the list of places (FR-P9, AM-314). [id] holds a position (`auto`, `root`, `sub.0`, `sub.1`, …), never a
 * subfolder name, so test tags and logs carry no user data. [assignment] null = "Automatycznie".
 */
internal data class PlaceChoice(
    val id: String,
    val assignment: PlaceAssignment?,
    val label: UiText,
)

/** Pure rules of the list of places of a session in a group. */
internal object PlaceChoices {
    private const val AUTO = "auto"
    private const val ROOT = "root"

    /** "Automatycznie", "folder główny", then the subfolders of [project] in `ProjectGroups.order`. */
    fun of(project: Project): List<PlaceChoice> =
        listOf(
            PlaceChoice(AUTO, null, UiText("place.choice.auto")),
            PlaceChoice(ROOT, PlaceAssignment.ProjectRoot, UiText("place.choice.root")),
        ) +
            ordered(project).mapIndexed { index, name ->
                PlaceChoice(
                    "sub.$index",
                    PlaceAssignment.Subfolder(name.value),
                    UiText("place.choice.subfolder", listOf(name.value)),
                )
            }

    /** The id of the choice that holds for [session] now, by [Session.assignedDir]; `auto` if null or unknown. */
    fun current(
        project: Project,
        session: Session,
    ): String {
        val dir = session.assignedDir
        val index = ordered(project).indexOfFirst { ProjectGroups.dirOf(project, it) == dir }
        return when {
            dir == null -> AUTO
            dir == project.dir -> ROOT
            index < 0 -> AUTO
            else -> "sub.$index"
        }
    }

    private fun ordered(project: Project) = project.subfolders.sortedWith(ProjectGroups.order)
}
