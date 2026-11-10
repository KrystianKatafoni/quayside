package dev.quayside.projects.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmIconButton
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.IconButtonOptions
import dev.quayside.core.designsystem.IconButtonSize
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.TextRole
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.ProjectGroups

/**
 * The sidebar with its dialogs; stateless apart from [controller]. [addKeyCap] is the text of the shortcut shown on
 * the "Dodaj projekt z dysku…" button; null shows none.
 */
@Composable
public fun ProjectsSidebar(
    controller: ProjectsController,
    modifier: Modifier = Modifier,
    addKeyCap: String? = null,
) {
    val state by controller.state.collectAsState()
    // The dialog popup sits outside the Column, so it takes no slot in its spacing.
    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .width(width = 260.dp)
                    .fillMaxHeight()
                    .background(color = panelColor())
                    .padding(all = 12.dp)
                    .testTag(ProjectsTags.SIDEBAR),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            SidebarHeader { controller.onIntent(ProjectsIntent.AddRequested) }
            state.banner?.let { Banner(it) { controller.onEvent(SidebarEvent.DismissBanner) } }
            ProjectList(state, controller)
            AddFromDiskButton(addKeyCap) { controller.onIntent(ProjectsIntent.AddRequested) }
        }
        state.dialog?.let { ProjectsDialog(it, controller::onEvent) }
    }
}

/** "PROJEKTY" (LABEL role) with the "+" icon button that adds a project; it never takes focus. */
@Composable
private fun SidebarHeader(onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(weight = 1f)) {
            AmText(ProjectsStrings["sidebar.title"], role = TextRole.LABEL, color = mutedColor())
        }
        AmIconButton(
            IconKind.PLUS,
            ProjectsStrings["sidebar.add"],
            onAdd,
            Modifier.testTag(ProjectsTags.ADD_BUTTON),
            IconButtonOptions(focusable = false, size = IconButtonSize.COMPACT),
        )
    }
}

@Composable
private fun ColumnScope.ProjectList(
    state: ProjectsUiState,
    controller: ProjectsController,
) {
    val list = Modifier.weight(weight = 1f).fillMaxWidth()
    if (state.projects.isEmpty()) {
        Box(modifier = list.testTag(ProjectsTags.EMPTY), contentAlignment = Alignment.Center) {
            AmText(
                ProjectsStrings["sidebar.empty"],
                color = mutedColor(),
            )
        }
    } else {
        Column(
            modifier = list.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        ) {
            state.projects.forEach { project -> ProjectWithSubfolders(project, state, controller) }
        }
    }
}

/** The row of [project] and, when it is an expanded group, the rows of its subfolders. */
@Composable
private fun ProjectWithSubfolders(
    project: Project,
    state: ProjectsUiState,
    controller: ProjectsController,
) {
    val group = project.subfolders.isNotEmpty()
    val collapsed = project.id in state.collapsed
    val onProject = state.selected == project.id && state.subfolder == null
    ProjectRow(
        project = project,
        state = RowState(onProject, state.statusesOf(ProjectFocus(project.id)), collapsed.takeIf { group }),
        actions = rowActions(project, controller),
    )
    if (!collapsed) {
        val subfolders = project.subfolders.sortedWith(ProjectGroups.order)
        subfolders.forEach { name ->
            val focus = ProjectFocus(project.id, name)
            SubfolderRow(
                id = project.id,
                name = name,
                state = RowState(state.focusIs(focus), state.statusesOf(focus)),
                last = name == subfolders.last(),
                onSelect = { controller.onEvent(SidebarEvent.Select(project.id, name)) },
            )
        }
    }
}

private fun rowActions(
    project: Project,
    controller: ProjectsController,
): RowActions {
    val onEvent = controller::onEvent
    return RowActions(
        select = { onEvent(SidebarEvent.Select(project.id)) },
        rename = { onEvent(SidebarEvent.RenameRequested(project.id)) },
        remove = {
            onEvent(SidebarEvent.Select(project.id))
            controller.onIntent(ProjectsIntent.RemoveSelectedRequested)
        },
        subfolders = { onEvent(SidebarEvent.EditSubfoldersRequested(project.id)) },
        toggleGroup = { onEvent(SidebarEvent.ToggleGroup(project.id)) },
    )
}

private fun ProjectsUiState.statusesOf(focus: ProjectFocus): StatusCounts = sessions.statuses[focus] ?: StatusCounts()

private fun ProjectsUiState.focusIs(focus: ProjectFocus): Boolean =
    selected == focus.project && subfolder == focus.subfolder

@Composable
private fun Banner(
    text: UiText,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = warnBgColor())
                .padding(all = 8.dp)
                .testTag(ProjectsTags.BANNER),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        AmText(ProjectsStrings.format(text), maxLines = Int.MAX_VALUE)
        TextButton(ProjectsStrings["banner.dismiss"], ProjectsTags.BANNER_DISMISS, onClick = onDismiss)
    }
}

/**
 * A clickable text on a raised background: Material is not a dependency (AM-304 plan, decision 4). Never focusable:
 * outside a focusable `Popup` a focused button would keep the focus after a click and eat the window shortcuts `⏎`
 * and space. Focusable buttons of the dialogs and of the row menu are `AmButton`.
 */
@Composable
internal fun TextButton(
    text: String,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .testTag(tag)
                .focusProperties { canFocus = false }
                .clip(AmTheme.shape.corner)
                .clickable(role = Role.Button, onClick = onClick)
                .background(color = raisedColor())
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        AmText(text)
    }
}
