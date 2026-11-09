package dev.quayside.notifications.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.VoiceId
import dev.quayside.projects.api.Project

/** The exception of one registered project as the dialog shows it (FR-N7); [voice] null = the global voice. */
public data class ProjectDraft(
    val id: ProjectId,
    val name: String,
    val profile: ProjectProfile,
    val voice: VoiceId?,
)

/** The rows for [projects] sorted by name ignoring case, each with its [stored] exception or the default. */
internal fun projectDraftsOf(
    projects: List<Project>,
    stored: Map<ProjectId, ProjectNotifications>,
): List<ProjectDraft> =
    projects.sortedBy { it.displayName.lowercase() }.map { project ->
        val exception = stored[project.id] ?: ProjectNotifications()
        ProjectDraft(project.id, project.displayName, exception.profile, exception.voice)
    }

/** [current] without the ids of [rows], plus the rows that differ from the default. */
internal fun projectsOf(
    rows: List<ProjectDraft>,
    current: Map<ProjectId, ProjectNotifications>,
): Map<ProjectId, ProjectNotifications> {
    val changed =
        rows
            .associate { it.id to ProjectNotifications(it.profile, it.voice) }
            .filterValues { it != ProjectNotifications() }
    return current.filterKeys { id -> rows.none { it.id == id } } + changed
}
