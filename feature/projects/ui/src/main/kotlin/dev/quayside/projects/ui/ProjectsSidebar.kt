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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toColor
import dev.quayside.projects.api.Project

/** The sidebar with its dialogs; stateless apart from [controller]. */
@Composable
public fun ProjectsSidebar(
    controller: ProjectsController,
    modifier: Modifier = Modifier,
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
            AmText(
                ProjectsStrings["sidebar.title"],
                color = mutedColor(),
            )
            state.banner?.let { Banner(it) { controller.onEvent(SidebarEvent.DismissBanner) } }
            ProjectList(state, controller)
            TextButton(ProjectsStrings["sidebar.add"], ProjectsTags.ADD_BUTTON) {
                controller.onIntent(ProjectsIntent.AddRequested)
            }
        }
        state.dialog?.let { ProjectsDialog(it, controller::onEvent) }
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
        Column(modifier = list.verticalScroll(rememberScrollState())) {
            state.projects.forEach { project ->
                ProjectRow(
                    project = project,
                    selected = project.id == state.selected,
                    badges = CountBadges.of(state.sessions.counts[project.id]),
                    onEvent = controller::onEvent,
                    onRemove = {
                        controller.onEvent(SidebarEvent.Select(project.id))
                        controller.onIntent(ProjectsIntent.RemoveSelectedRequested)
                    },
                )
            }
        }
    }
}

/**
 * The row never takes focus: a focused `selectable` would consume `space` and Enter before the window shortcuts. A
 * click requests focus anyway and an unfocusable parent hands it to its first focusable child, so the ⋯ button of the
 * row is unfocusable too.
 */
@Composable
private fun ProjectRow(
    project: Project,
    selected: Boolean,
    badges: List<UiText>,
    onEvent: (SidebarEvent) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ProjectsTags.row(project.id))
                .focusProperties { canFocus = false }
                .selectable(selected = selected, onClick = { onEvent(SidebarEvent.Select(project.id)) })
                .background(
                    color = if (selected) selColor() else Color.Transparent,
                ).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(weight = 1f)) {
            AmText(project.displayName)
            Counters(badges)
        }
        RowMenu(project, { onEvent(SidebarEvent.RenameRequested(project.id)) }, onRemove)
    }
}

/** "waiting: n" (highlighted, it needs the user) and "working: n"; nothing for zeros. */
@Composable
private fun Counters(badges: List<UiText>) {
    if (badges.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        badges.forEach { badge ->
            AmText(ProjectsStrings.format(badge), role = TextRole.CAPTION, color = badgeColor(badge))
        }
    }
}

@Composable
private fun badgeColor(badge: UiText): Color {
    val colors = AmTheme.colors
    return (if (badge.key == CountBadges.WAITING_KEY) colors.status.warn else colors.content.muted).toColor()
}

/** The ⋯ button of a row with its menu: rename and remove. */
@Composable
private fun RowMenu(
    project: Project,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton("⋯", ProjectsTags.rowMenu(project.id)) { open = true }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column {
                    MenuButton("row.rename", ProjectsTags.ROW_MENU_RENAME) {
                        open = false
                        onRename()
                    }
                    MenuButton("row.remove", ProjectsTags.ROW_MENU_REMOVE) {
                        open = false
                        onRemove()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuButton(
    key: String,
    tag: String,
    onClick: () -> Unit,
) {
    AmButton(label = ProjectsStrings[key], onClick = onClick, modifier = Modifier.testTag(tag))
}

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
