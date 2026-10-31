package dev.quayside.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.quayside.commandpalette.ui.CommandPalette
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.missioncontrol.ui.GlobalViews
import dev.quayside.missioncontrol.ui.MissionControl
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.projects.ui.ProjectsController
import dev.quayside.projects.ui.ProjectsIntent
import dev.quayside.projects.ui.ProjectsSidebar
import kotlinx.coroutines.flow.filterNotNull

/**
 * The window content: the projects sidebar with the global views under it, next to Mission Control (AM-304,
 * AM-405, AM-407). A shown global view clears the project in the sidebar (AM-407, decision 1).
 *
 * [terminalPanel], when given, draws the terminal under Mission Control; it gets the size to take and draws nothing
 * while there are no tabs and no error (AM-503). [attacher], when given, serves the mouse paths to the terminal:
 * the Attach and Odpowiedz buttons, the row menu and a double click (AM-511); it is the one `⏎` and `r` use.
 */
@Composable
@Suppress("LongParameterList") // the sidebar, centre, palette and the two terminal parts of one window (AM-511)
internal fun QuaysideApp(
    projects: ProjectsController,
    missionControl: MissionControlController,
    palette: CommandPaletteController,
    modifier: Modifier = Modifier,
    terminalPanel: (@Composable (Modifier) -> Unit)? = null,
    attacher: SessionAttacher? = null,
) {
    LaunchedEffect(projects, missionControl) {
        missionControl.view.filterNotNull().collect { projects.onIntent(ProjectsIntent.ClearSelection) }
    }
    Row(modifier = modifier.fillMaxSize()) {
        Column {
            ProjectsSidebar(projects, Modifier.weight(weight = 1f))
            GlobalViews(missionControl)
        }
        Column(modifier = Modifier.weight(weight = 1f)) {
            MissionControl(
                missionControl,
                Modifier.weight(weight = 1f).fillMaxWidth(),
                onAttach = { attacher?.attachSelected() },
                onReply = { attacher?.replySelected() },
            )
            terminalPanel?.invoke(Modifier.fillMaxWidth().height(TERMINAL_HEIGHT))
        }
        CommandPalette(palette)
    }
}

/** The height of the terminal panel under Mission Control. */
private val TERMINAL_HEIGHT = 320.dp
