package dev.quayside.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.SessionBar
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsRail
import dev.quayside.terminal.ui.TabKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the terminal mode needs besides the modifier: the controllers, the mode and the terminal panel. */
internal class TerminalModeParts(
    val projects: ProjectsController,
    val missionControl: MissionControlController,
    val mode: WindowModeState,
    val panel: (@Composable (Modifier, (() -> Unit)?) -> Unit)?,
    val terminalKey: StateFlow<TabKey?>? = null,
    val attacher: SessionAttacher? = null,
)

/**
 * The terminal mode (docs/06, mockup B): a narrow bar of project icons with "Wróć do listy sesji" above it, and next
 * to it the session bar of the active attach tab over the terminal. Neither the sidebar nor Mission Control is drawn.
 * Nothing here takes focus, so the keys of the window and of the terminal keep working.
 */
@Composable
internal fun TerminalModeLayout(
    modifier: Modifier,
    parts: TerminalModeParts,
) {
    val key by (parts.terminalKey ?: NO_KEY).collectAsState()
    Row(modifier = modifier.fillMaxHeight()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BackButton(parts.mode)
            ProjectsRail(parts.projects, { parts.mode.onEvent(WindowModeEvent.ProjectPicked) }, Modifier.weight(1f))
        }
        Column(modifier = Modifier.weight(weight = 1f)) {
            SessionBar(
                parts.missionControl,
                key?.let(::sessionOfTabKey),
                onReply = { parts.attacher?.replySelected() },
                modifier = Modifier.fillMaxWidth(),
            )
            parts.panel?.invoke(Modifier.weight(weight = 1f).fillMaxWidth(), null)
        }
    }
}

private val NO_KEY: StateFlow<TabKey?> = MutableStateFlow(null)

@Composable
private fun BackButton(mode: WindowModeState) {
    AmIconButton(
        IconKind.BACK,
        Strings["command.backToList"],
        { mode.onEvent(WindowModeEvent.Back) },
        Modifier.testTag(ModeTags.BACK).padding(top = 8.dp),
        IconButtonOptions(focusable = false, size = IconButtonSize.LARGE, style = IconButtonStyle.OUTLINED),
    )
}
