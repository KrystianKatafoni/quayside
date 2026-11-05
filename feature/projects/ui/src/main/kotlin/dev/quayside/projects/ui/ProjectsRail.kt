package dev.quayside.projects.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmIconButton
import dev.quayside.core.designsystem.IconButtonOptions
import dev.quayside.core.designsystem.IconButtonSize
import dev.quayside.core.designsystem.IconButtonStyle
import dev.quayside.core.designsystem.IconKind
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.SessionCounts

/**
 * Collapsed sidebar of terminal mode: one LARGE icon button per project (folder in project tone, SELECTED when
 * selected, warn badge with the waiting count, label "name, 2 czekają, 1 pracuje"), then "+" (add project).
 * A click selects the project and calls [onProjectClick]. Hosts the projects dialogs while shown.
 */
@Composable
public fun ProjectsRail(
    controller: ProjectsController,
    onProjectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    // The dialog popup sits outside the Column, as in the sidebar.
    Box(modifier = modifier) {
        RailColumn(controller, state, onProjectClick)
        state.dialog?.let { ProjectsDialog(it, controller::onEvent) }
    }
}

@Composable
private fun RailColumn(
    controller: ProjectsController,
    state: ProjectsUiState,
    onProjectClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(width = 64.dp)
                .fillMaxHeight()
                .background(color = panelColor())
                .padding(vertical = 8.dp)
                .testTag(ProjectsTags.RAIL),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.projects.forEach { project ->
            RailButton(project, project.id == state.selected, state.sessions.counts[project.id]) {
                controller.onEvent(SidebarEvent.Select(project.id))
                onProjectClick()
            }
        }
        AmIconButton(
            IconKind.PLUS,
            ProjectsStrings["sidebar.add"],
            { controller.onIntent(ProjectsIntent.AddRequested) },
            Modifier.testTag(ProjectsTags.RAIL_ADD),
            IconButtonOptions(focusable = false, size = IconButtonSize.LARGE),
        )
    }
}

/** Never takes focus, like the rows of the sidebar. */
@Composable
private fun RailButton(
    project: Project,
    selected: Boolean,
    counts: SessionCounts?,
    onClick: () -> Unit,
) {
    AmIconButton(
        IconKind.FOLDER,
        railLabel(project.displayName, counts),
        onClick,
        Modifier.testTag(ProjectsTags.railButton(project.id)),
        IconButtonOptions(
            focusable = false,
            tone = projectTone(counts),
            size = IconButtonSize.LARGE,
            style = if (selected) IconButtonStyle.SELECTED else IconButtonStyle.GHOST,
            badge = counts?.waiting?.takeIf { it > 0 }?.toString(),
        ),
    )
}
