package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.ProjectFocus
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the sidebar reports to Mission Control, set from a test: assign a project with [value], or a project with a
 * subfolder through [focus]. Stands for `ProjectsController.focus`.
 */
internal class SelectedProject(
    initial: ProjectId? = null,
) {
    val focus = MutableStateFlow(initial?.let { ProjectFocus(it) })

    /** The project of the focus; assigning it focuses the whole project (no subfolder). */
    var value: ProjectId?
        get() = focus.value?.project
        set(project) {
            focus.value = project?.let { ProjectFocus(it) }
        }
}
