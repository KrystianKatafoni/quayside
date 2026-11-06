package dev.quayside.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmPanel
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole

/** A dialog box tagged [tag]; merged for screen readers, so its texts are read as one. */
@Composable
internal fun Panel(
    tag: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    AmPanel(modifier = Modifier.width(width = 440.dp).testTag(tag).semantics(mergeDescendants = true) {}) {
        Column(modifier = Modifier.padding(all = 16.dp), verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            AmText(title, role = TextRole.HEADING, maxLines = Int.MAX_VALUE)
            content()
        }
    }
}

/** Text of a dialog; wraps, as paths and messages are long. [muted] only on the panel. */
@Composable
internal fun DialogText(
    text: String,
    muted: Boolean = false,
) {
    val color =
        if (muted) mutedColor() else null
    AmText(text, color = color, maxLines = Int.MAX_VALUE)
}

@Composable
internal fun DialogButton(
    label: String,
    tag: String,
    focus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val modifier = if (focus == null) Modifier else Modifier.focusRequester(focus)
    AmButton(label = label, onClick = onClick, modifier = modifier.testTag(tag))
}

/** "Anuluj"; with [focus] it takes the start focus (a button that changes nothing, NFR-5). */
@Composable
internal fun Cancel(
    focus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    DialogButton(ProjectsStrings["dialog.cancel"], ProjectsTags.CANCEL, focus, onClick)
}
