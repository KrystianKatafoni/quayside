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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmChip
import dev.quayside.core.designsystem.AmIcon
import dev.quayside.core.designsystem.AmIconButton
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.IconButtonOptions
import dev.quayside.core.designsystem.IconButtonSize
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designsystem.markColor
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.SessionCounts

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
            SidebarHeader { controller.onIntent(ProjectsIntent.AddRequested) }
            state.banner?.let { Banner(it) { controller.onEvent(SidebarEvent.DismissBanner) } }
            ProjectList(state, controller)
        }
        state.dialog?.let { ProjectsDialog(it, controller::onEvent) }
    }
}

/** "PROJEKTY" with the "+" icon button that adds a project; it never takes focus. */
@Composable
private fun SidebarHeader(onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(weight = 1f)) {
            AmText(ProjectsStrings["sidebar.title"], color = mutedColor())
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
        Column(modifier = list.verticalScroll(rememberScrollState())) {
            state.projects.forEach { project ->
                ProjectRow(
                    project = project,
                    selected = project.id == state.selected,
                    counts = state.sessions.counts[project.id],
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
 * row is unfocusable too. The selection is taken by a layer behind the cells, so the count chips are separate nodes
 * of the semantics tree; the tagged node itself carries the name and says whether the row is selected.
 */
@Composable
private fun ProjectRow(
    project: Project,
    selected: Boolean,
    counts: SessionCounts?,
    onEvent: (SidebarEvent) -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ProjectsTags.row(project.id))
                .semantics {
                    this.selected = selected
                    text = AnnotatedString(project.displayName)
                }.background(color = if (selected) selColor() else Color.Transparent),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .focusProperties { canFocus = false }
                .selectable(selected = selected, onClick = { onEvent(SidebarEvent.Select(project.id)) }),
        )
        RowCells(project, counts, { onEvent(SidebarEvent.RenameRequested(project.id)) }, onRemove)
    }
}

/** Folder in the tone of the project, its name over the count chips, and the ⋯ menu. */
@Composable
private fun RowCells(
    project: Project,
    counts: SessionCounts?,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        AmIcon(IconKind.FOLDER, null, Modifier.size(20.dp), projectTone(counts).markColor())
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
            AmText(project.displayName, Modifier.clearAndSetSemantics {})
            Counters(CountBadges.of(counts))
        }
        RowMenu(project, onRename, onRemove)
    }
}

/** "waiting: n" (warn, it needs the user) and "working: n" as chips with the number; nothing for zeros. */
@Composable
private fun Counters(badges: List<UiText>) {
    if (badges.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        badges.forEach { badge ->
            val tone = if (badge.key == CountBadges.WAITING_KEY) StatusTone.WARN else StatusTone.WORK
            AmChip(badge.args.first(), tone = tone, description = ProjectsStrings.format(badge))
        }
    }
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
