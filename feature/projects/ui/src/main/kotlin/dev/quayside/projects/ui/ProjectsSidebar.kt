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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
                    .background(color = SidebarColors.background)
                    .padding(all = 12.dp)
                    .testTag(ProjectsTags.SIDEBAR),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Label(ProjectsStrings["sidebar.title"], SidebarColors.secondaryText)
            state.banner?.let { Banner(it) { controller.onEvent(SidebarEvent.DismissBanner) } }
            ProjectList(state, controller)
            TextButton(ProjectsStrings["sidebar.add"], ProjectsTags.ADD_BUTTON, focusable = false) {
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
            Label(ProjectsStrings["sidebar.empty"], SidebarColors.secondaryText)
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
                .background(color = if (selected) SidebarColors.selection else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(weight = 1f)) {
            BasicText(
                text = project.displayName,
                style = TextStyle(color = SidebarColors.text, fontSize = 14.sp),
                maxLines = 1,
            )
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
            val color = badgeColor(badge)
            Label(ProjectsStrings.format(badge), color)
        }
    }
}

private fun badgeColor(badge: UiText): Color =
    if (badge.key == CountBadges.WAITING_KEY) SidebarColors.attention else SidebarColors.secondaryText

/** The ⋯ button of a row with its menu: rename and remove. */
@Composable
private fun RowMenu(
    project: Project,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton("⋯", ProjectsTags.rowMenu(project.id), focusable = false) { open = true }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column {
                    TextButton(ProjectsStrings["row.rename"], ProjectsTags.ROW_MENU_RENAME) {
                        open = false
                        onRename()
                    }
                    TextButton(ProjectsStrings["row.remove"], ProjectsTags.ROW_MENU_REMOVE) {
                        open = false
                        onRemove()
                    }
                }
            }
        }
    }
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
                .background(color = SidebarColors.warning)
                .padding(all = 8.dp)
                .testTag(ProjectsTags.BANNER),
        verticalArrangement = Arrangement.spacedBy(space = 4.dp),
    ) {
        Label(ProjectsStrings.format(text), SidebarColors.text)
        TextButton(
            ProjectsStrings["banner.dismiss"],
            ProjectsTags.BANNER_DISMISS,
            focusable = false,
            onClick = onDismiss,
        )
    }
}

/** A clickable text: Material is not a dependency (AM-304 plan, decision 4). A click focuses it only if [focusable].
 * Pass `focusable = false` outside a focusable `Popup`; otherwise the button keeps the focus after a click and eats the
 * window shortcuts `⏎` and space.
 */
@Composable
internal fun TextButton(
    text: String,
    tag: String,
    focusable: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .testTag(tag)
                .focusProperties { canFocus = focusable }
                .clickable(role = Role.Button, onClick = onClick)
                .background(color = SidebarColors.button)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        BasicText(text = text, style = TextStyle(color = SidebarColors.text, fontSize = 13.sp))
    }
}

@Composable
internal fun Label(
    text: String,
    color: Color = SidebarColors.text,
) {
    BasicText(text = text, style = TextStyle(color = color, fontSize = 13.sp))
}

/** Placeholder colors until `core:design-system` and `AmTheme` exist (AM-802). */
internal object SidebarColors {
    val background = Color(color = 0xFF2C2C2E)
    val selection = Color(color = 0xFF3A3A3C)
    val button = Color(color = 0xFF48484A)
    val warning = Color(color = 0xFF5C4400)
    val scrim = Color(color = 0x99000000)
    val dialog = Color(color = 0xFF2C2C2E)
    val text = Color(color = 0xFFF2F2F7)
    val secondaryText = Color(color = 0xFF8E8E93)
    val attention = Color(color = 0xFFFFB340)
}
