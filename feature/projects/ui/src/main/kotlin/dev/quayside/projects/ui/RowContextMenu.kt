package dev.quayside.projects.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.quayside.core.designsystem.AmButton

private fun Modifier.closesOnEscape(onClose: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        (event.type == KeyEventType.KeyDown && event.key == Key.Escape).also { if (it) onClose() }
    }

/** The menu of a project row at [anchor] (where the right click was): rename, subfolders, remove. */
@Composable
internal fun RowContextMenu(
    anchor: IntOffset,
    actions: RowActions,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    Popup(
        alignment = Alignment.TopStart,
        offset = anchor,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        // Inside the popup: without a focused node of its own the popup gets no keys and Escape would not close it.
        LaunchedEffect(Unit) { focus.requestFocus() }
        Column(Modifier.focusRequester(focus).focusable().closesOnEscape(onDismiss)) {
            MenuButton("row.rename", ProjectsTags.ROW_MENU_RENAME) {
                onDismiss()
                actions.rename()
            }
            MenuButton("row.subfolders", ProjectsTags.ROW_MENU_SUBFOLDERS) {
                onDismiss()
                actions.subfolders()
            }
            MenuButton("row.remove", ProjectsTags.ROW_MENU_REMOVE) {
                onDismiss()
                actions.remove()
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
