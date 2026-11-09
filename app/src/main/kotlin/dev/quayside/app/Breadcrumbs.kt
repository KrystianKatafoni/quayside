package dev.quayside.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmLogo
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmWordmark
import dev.quayside.core.designsystem.TextRole
import dev.quayside.missioncontrol.ui.GlobalView
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.title
import dev.quayside.terminal.ui.TabKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The crumbs of the top bar: "Quayside", then the title of the [view] when a global view is shown, else the [project];
 * in the terminal mode the [session] name of the active tab comes last.
 */
internal fun crumbsOf(
    project: String?,
    view: GlobalView?,
    session: String?,
): List<String> = listOfNotNull(Strings["crumb.root"], view?.title() ?: project, session)

/** The [parts] of the crumbs and whether the last one is a session name (drawn in the name role). */
internal class Crumbs(
    val parts: List<String>,
    val endsWithSession: Boolean,
)

/**
 * The crumbs of the window now: the shown project or view, and in the terminal mode ([terminalMode]) the name of the
 * session of the active attach tab ([tabKey]; none for a shell tab).
 */
@Composable
internal fun rememberCrumbs(
    missionControl: MissionControlController,
    tabKey: StateFlow<TabKey?>?,
    terminalMode: Boolean,
): Crumbs {
    val names by missionControl.names.collectAsState()
    val view by missionControl.view.collectAsState()
    val key by (tabKey ?: NO_KEY).collectAsState()
    val session = key?.takeIf { terminalMode }?.let(::sessionOfTabKey)?.let(names.sessions::get)
    return Crumbs(crumbsOf(names.project, view, session), session != null)
}

private val NO_KEY: StateFlow<TabKey?> = MutableStateFlow(null)

/**
 * The mark, then "Quayside / project / session": the root crumb is the wordmark, the last crumb is in the text colour,
 * the others muted. The mark is decorative for the keys (never focusable) but has a label for accessibility.
 */
@Composable
internal fun Breadcrumbs(
    crumbs: Crumbs,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag(TopBarTags.CRUMBS),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmLogo(LOGO_SIZE, Strings["topBar.logo"])
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            crumbs.parts.forEachIndexed { index, crumb ->
                if (index > 0) AmText("/", color = mutedColor())
                if (index == 0) {
                    RootCrumb()
                } else {
                    Crumb(crumb, last = index == crumbs.parts.lastIndex, endsWithSession = crumbs.endsWithSession)
                }
            }
        }
    }
}

private val LOGO_SIZE = 20.dp

/** The root crumb: "Quayside" as the wordmark (the text of `crumb.root` stays the first of [crumbsOf]). */
@Composable
private fun RootCrumb() {
    AmWordmark()
}

@Composable
private fun Crumb(
    text: String,
    last: Boolean,
    endsWithSession: Boolean,
) {
    val role = if (last && endsWithSession) TextRole.NAME else TextRole.BODY
    AmText(text, role = role, color = if (last) null else mutedColor())
}
