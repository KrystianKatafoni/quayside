package dev.quayside.projects.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.amScrim

/**
 * The open [dialog] over the whole window, inside it rather than a separate window (AM-304 plan, decision 4).
 * Escape is "Cancel"; the controller ignores it while a confirmed command runs. Nothing is shown while the
 * system folder chooser is open ([Dialog.Picking]).
 */
@Composable
internal fun ProjectsDialog(
    dialog: Dialog,
    onEvent: (SidebarEvent) -> Unit,
) {
    if (dialog == Dialog.Picking) return
    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = { onEvent(SidebarEvent.Dismiss) },
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().amScrim(),
            contentAlignment = Alignment.Center,
        ) {
            DialogContent(dialog, onEvent)
        }
    }
}

@Composable
private fun DialogContent(
    dialog: Dialog,
    onEvent: (SidebarEvent) -> Unit,
) {
    val cancel = { onEvent(SidebarEvent.Dismiss) }
    when (dialog) {
        // Not shown, see ProjectsDialog.
        Dialog.Picking -> Unit

        is Dialog.Waiting -> Waiting(dialog, onCancel = cancel.takeIf { dialog != Dialog.Waiting.BUSY })

        is Dialog.ConfirmAdd -> ConfirmAdd(dialog, onEvent)

        is Dialog.ConfirmRemove -> ConfirmRemove(dialog, onEvent)

        is Dialog.Rename -> Rename(dialog, onEvent)

        is Dialog.Subfolders -> SubfoldersDialog(dialog, onEvent)

        is Dialog.Message -> Message(dialog.text, cancel)
    }
}

/** A dialog waiting for a step to finish; [onCancel] null = the step cannot be cancelled. */
@Composable
private fun Waiting(
    step: Dialog.Waiting,
    onCancel: (() -> Unit)?,
) {
    Panel(step.tag, ProjectsStrings[step.key]) { onCancel?.let { Cancel(onClick = it) } }
}

@Composable
private fun Message(
    text: UiText,
    onClose: () -> Unit,
) {
    Panel(ProjectsTags.MESSAGE, ProjectsStrings.format(text)) {
        DialogButton(ProjectsStrings["dialog.close"], ProjectsTags.CANCEL, onClick = onClose)
    }
}

@Composable
private fun ConfirmAdd(
    dialog: Dialog.ConfirmAdd,
    onEvent: (SidebarEvent) -> Unit,
) {
    Panel(ProjectsTags.CONFIRM_ADD, ProjectsStrings["add.title"]) {
        // The path is user data: shown here, never logged (ENG-C8, KDoc of `ProjectError`).
        DialogText(dialog.report.dir.toString())
        ReportSummary.lines(dialog.report).forEach { DialogText(ProjectsStrings.format(it), muted = true) }
        dialog.error?.let { DialogText(ProjectsStrings.format(it)) }
        DialogButton(ProjectsStrings["add.confirm"], ProjectsTags.ADD) { onEvent(SidebarEvent.ConfirmAdd) }
        Cancel { onEvent(SidebarEvent.Dismiss) }
    }
}

@Composable
private fun Rename(
    dialog: Dialog.Rename,
    onEvent: (SidebarEvent) -> Unit,
) {
    Panel(ProjectsTags.RENAME_DIALOG, ProjectsStrings["rename.title"]) {
        RenameField(dialog.draft, onEvent)
        dialog.error?.let { DialogText(ProjectsStrings.format(it)) }
        DialogButton(ProjectsStrings["rename.save"], ProjectsTags.RENAME_SAVE) { onEvent(SidebarEvent.ConfirmRename) }
        Cancel { onEvent(SidebarEvent.Dismiss) }
    }
}

/** Text field with the whole name selected; Enter saves, Escape cancels (handled here, there is no Material). */
@Composable
private fun RenameField(
    draft: String,
    onEvent: (SidebarEvent) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(draft, TextRange(0, draft.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = field,
        onValueChange = {
            field = it
            if (it.text != draft) onEvent(SidebarEvent.NameChanged(it.text))
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = raisedColor())
                .padding(all = 6.dp)
                .focusRequester(focus)
                .onPreviewKeyEvent { handleRenameKey(it, onEvent) }
                .testTag(ProjectsTags.RENAME_FIELD),
        singleLine = true,
        textStyle = AmTheme.typography.style(TextRole.BODY).copy(color = textColor()),
        cursorBrush = SolidColor(textColor()),
    )
}

private fun handleRenameKey(
    event: KeyEvent,
    onEvent: (SidebarEvent) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.Enter, Key.NumPadEnter -> true.also { onEvent(SidebarEvent.ConfirmRename) }
        Key.Escape -> true.also { onEvent(SidebarEvent.Dismiss) }
        else -> false
    }
}

/** Places the dialog layer over the whole window, whatever the anchor. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
