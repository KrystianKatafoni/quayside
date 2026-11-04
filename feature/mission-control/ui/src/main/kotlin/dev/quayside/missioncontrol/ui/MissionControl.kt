package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toColor

/**
 * The centre of the window: header, filter, session list, details panel and mode bar; stateless apart from
 * [controller]. [onAttach] and [onReply] open the selected session in the terminal (`⏎`, `r`); the buttons, the row
 * menu and a double click on a row call them (AM-511). [onNewAgent] opens the "Nowy agent" dialog (AM-608). They do
 * nothing by default.
 */
@Composable
public fun MissionControl(
    controller: MissionControlController,
    modifier: Modifier = Modifier,
    onAttach: () -> Unit = {},
    onReply: () -> Unit = {},
    onNewAgent: () -> Unit = {},
) {
    val state by controller.state.collectAsState()
    val onEvent: (MissionControlEvent) -> Unit = { event ->
        when (event) {
            MissionControlEvent.Attach -> onAttach()
            MissionControlEvent.Reply -> onReply()
            MissionControlEvent.NewAgent -> onNewAgent()
            else -> controller.onEvent(event)
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(color = bgColor())
                .testTag(MissionControlTags.ROOT),
    ) {
        Box(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
            when (val screen = state.screen) {
                Screen.NoProjects -> Empty("empty.noProjects", "empty.noProjects.hint")
                Screen.NoSelection -> Empty("empty.noSelection", hint = null)
                is Screen.Project -> ProjectPage(screen, state.preview, onEvent)
                is Screen.Global -> GlobalPage(screen, state.preview, onEvent)
            }
        }
        ModeBarView(state.modeBar)
    }
    state.dialog?.let { SessionDialogView(it, onEvent) }
}

@Composable
private fun Empty(
    title: String,
    hint: String?,
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag(MissionControlTags.EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AmText(MissionControlStrings[title], role = TextRole.TITLE)
        hint?.let { AmText(MissionControlStrings[it], color = mutedColor()) }
    }
}

@Composable
private fun ProjectPage(
    page: Screen.Project,
    preview: PreviewUi?,
    onEvent: (MissionControlEvent) -> Unit,
) {
    val filters = @Composable { Filters(page.filter, onEvent) }
    SessionPage(page.banner, page.loading, page.rows, page.details, preview, onEvent, filters) {
        Header(page.header, onEvent)
    }
}

/** The list and the details panel shared by the project page and the global views. */
@Composable
@Suppress("LongParameterList") // all parts of one page; the two callers pass them straight through
internal fun SessionPage(
    banner: UiText?,
    loading: Boolean,
    rows: List<SessionRow>,
    details: SessionDetails?,
    preview: PreviewUi?,
    onEvent: (MissionControlEvent) -> Unit,
    filters: @Composable () -> Unit,
    header: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(weight = 1f).fillMaxHeight().padding(all = 12.dp),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            header()
            banner?.let {
                Text(
                    it,
                    Modifier.fillMaxWidth().background(color = warnBgColor()),
                    MissionControlTags.BANNER,
                )
            }
            filters()
            if (loading) {
                Text(UiText("list.loading"), tag = MissionControlTags.LOADING)
            } else {
                val listWeight = if (preview == null) 1f else LIST_SHARE
                SessionRows(rows, details, onEvent, Modifier.weight(weight = listWeight))
            }
            preview?.let { SessionPreviewView(it, onEvent, Modifier.weight(weight = 1f - LIST_SHARE)) }
        }
        Details(details, onEvent)
    }
}

@Composable
private fun Header(
    header: ProjectHeader,
    onEvent: (MissionControlEvent) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.testTag(MissionControlTags.HEADER)) {
            AmText(header.name, role = TextRole.TITLE)
            AmText(header.path.toString(), role = TextRole.CODE, color = mutedColor())
            AmText(MissionControlStrings.formatted("header.sessions", header.sessions))
        }
        NewAgentButton { onEvent(MissionControlEvent.NewAgent) }
    }
}

/** Never takes focus, like the filters: it would eat Space and Enter of the window (plan, rule 8). */
@Composable
private fun NewAgentButton(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .testTag(MissionControlTags.NEW_AGENT)
                .focusProperties { canFocus = false }
                .clip(AmTheme.shape.corner)
                .clickable(role = Role.Button, onClick = onClick)
                .background(color = raisedColor())
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        AmText(MissionControlStrings["newAgent.button"])
    }
}

/** The tabs never take focus either, for the same reason as the session rows. */
@Composable
private fun Filters(
    current: SessionFilter,
    onEvent: (MissionControlEvent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 4.dp)) {
        SessionFilter.entries.forEach { filter ->
            val surfaces = AmTheme.colors.surfaces
            Box(
                modifier =
                    Modifier
                        .testTag(MissionControlTags.filter(filter))
                        .focusProperties { canFocus = false }
                        .clip(AmTheme.shape.corner)
                        .clickable(role = Role.Tab) { onEvent(MissionControlEvent.SelectFilter(filter)) }
                        .background(color = (if (filter == current) surfaces.sel else surfaces.raised).toColor())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                AmText(MissionControlStrings["filter.${filter.name.lowercase()}"])
            }
        }
    }
}

@Composable
private fun Text(
    text: UiText,
    modifier: Modifier = Modifier,
    tag: String,
) {
    Box(modifier = modifier.testTag(tag).padding(all = 8.dp)) { AmText(MissionControlStrings.format(text)) }
}

/** The list takes this share of the middle column while the preview is open; the preview takes the rest. */
private const val LIST_SHARE = 0.6f
