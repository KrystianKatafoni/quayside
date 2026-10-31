package dev.agentmanager.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The centre of the window: header, filter, session list, details panel and mode bar; stateless apart from
 * [controller].
 */
@Composable
public fun MissionControl(
    controller: MissionControlController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    Column(modifier = modifier.fillMaxSize().background(color = Colors.background).testTag(MissionControlTags.ROOT)) {
        Box(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) {
            when (val screen = state.screen) {
                Screen.NoProjects -> Empty("empty.noProjects", "empty.noProjects.hint")
                Screen.NoSelection -> Empty("empty.noSelection", hint = null)
                is Screen.Project -> ProjectPage(screen, state.preview, controller::onEvent)
                is Screen.Global -> GlobalPage(screen, state.preview, controller::onEvent)
            }
        }
        ModeBarView(state.modeBar)
    }
    state.dialog?.let { SessionDialogView(it, controller::onEvent) }
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
        Label(MissionControlStrings[title], size = 20)
        hint?.let { Label(MissionControlStrings[it], Colors.secondary) }
    }
}

@Composable
private fun ProjectPage(
    page: Screen.Project,
    preview: PreviewUi?,
    onEvent: (MissionControlEvent) -> Unit,
) {
    val filters = @Composable { Filters(page.filter, onEvent) }
    SessionPage(page.banner, page.loading, page.rows, page.details, preview, onEvent, filters) { Header(page.header) }
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
                Text(it, Modifier.fillMaxWidth().background(color = Colors.warning), MissionControlTags.BANNER)
            }
            filters()
            if (loading) {
                Text(UiText("list.loading"), tag = MissionControlTags.LOADING)
            } else {
                val listWeight = if (preview == null) 1f else LIST_SHARE
                Column(modifier = Modifier.weight(weight = listWeight).verticalScroll(rememberScrollState())) {
                    rows.forEach { row -> SessionRowView(row) { onEvent(MissionControlEvent.SelectSession(row.id)) } }
                }
            }
            preview?.let { SessionPreviewView(it, onEvent, Modifier.weight(weight = 1f - LIST_SHARE)) }
        }
        Details(details, onEvent)
    }
}

@Composable
private fun Header(header: ProjectHeader) {
    Column(modifier = Modifier.testTag(MissionControlTags.HEADER)) {
        Label(header.name, size = 18)
        Label(header.path.toString(), Colors.secondary, mono = true)
        Label(MissionControlStrings.formatted("header.sessions", header.sessions))
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
            Box(
                modifier =
                    Modifier
                        .testTag(MissionControlTags.filter(filter))
                        .focusProperties { canFocus = false }
                        .clickable(role = Role.Tab) { onEvent(MissionControlEvent.SelectFilter(filter)) }
                        .background(color = if (filter == current) Colors.selection else Colors.button)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Label(MissionControlStrings["filter.${filter.name.lowercase()}"])
            }
        }
    }
}

/**
 * A row never takes focus: a focused `clickable` handles `space` and Enter as a click and consumes them before the
 * window shortcuts (`space` preview, later `⏎` attach) see them. A click requests focus anyway, and an unfocusable
 * node hands it to its first focusable child, so the children (only texts here) must not be focusable either.
 */
@Composable
private fun SessionRowView(
    row: SessionRow,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(MissionControlTags.row(row.id))
                .focusProperties { canFocus = false }
                .clickable(onClick = onClick)
                .background(color = if (row.selected) Colors.selection else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Box(
            Modifier
                .size(
                    width = 3.dp,
                    height = 28.dp,
                ).background(if (row.selected) Colors.accent else Color.Transparent),
        )
        Label(row.glyph.symbol, Colors.of(row.glyph.tone))
        Label(row.name, mono = true)
        row.project?.let { Label(MissionControlStrings.format(it), Colors.secondary) }
        Box(modifier = Modifier.weight(weight = 1f)) { Label(row.summary.orEmpty(), Colors.secondary) }
        Label(MissionControlStrings.format(row.lastEvent), Colors.secondary)
        Label(MissionControlStrings.format(row.age), Colors.secondary)
    }
}

@Composable
private fun Details(
    details: SessionDetails?,
    onEvent: (MissionControlEvent) -> Unit,
) {
    val panel =
        Modifier
            .width(width = 300.dp)
            .fillMaxHeight()
            .background(color = Colors.panel)
            .padding(all = 12.dp)
    if (details == null) {
        Box(modifier = panel.testTag(MissionControlTags.DETAILS_EMPTY), contentAlignment = Alignment.Center) {
            Label(MissionControlStrings["details.none"], Colors.secondary)
        }
        return
    }
    Column(
        modifier = panel.testTag(MissionControlTags.DETAILS),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Label(details.name, size = 18, mono = true)
        Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
            Label(details.glyph.symbol, Colors.of(details.glyph.tone))
            Label(MissionControlStrings[details.glyph.key])
        }
        details.summary?.let { Label(it) }
        SessionActionsView(details, onEvent)
        Field("details.worktree", details.worktree.toString())
        Field("details.id", details.id.value)
        Field("details.startedAt", timeFormat.format(details.startedAt))
        Field("details.lastActivity", timeFormat.format(details.lastActivityAt))
    }
}

@Composable
private fun Field(
    key: String,
    value: String,
) {
    Column {
        Label(MissionControlStrings[key], Colors.secondary)
        Label(value, mono = true)
    }
}

/**
 * `NORMAL`, the selected project (none in a global view), the counters of every project and the freshness of the
 * snapshot.
 */
@Composable
private fun ModeBarView(bar: ModeBar) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height = 28.dp)
                .background(color = Colors.panel)
                .padding(horizontal = 12.dp)
                .testTag(MissionControlTags.MODE_BAR),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
    ) {
        Label(bar.mode, Colors.accent, mono = true)
        bar.project?.let { Label(it) }
        Label(MissionControlStrings.formatted("modeBar.waiting", bar.waiting), Colors.of(Tone.WARN))
        Label(MissionControlStrings.formatted("modeBar.working", bar.working), Colors.of(Tone.WORK))
        Label(MissionControlStrings.format(bar.freshness), Colors.secondary)
    }
}

@Composable
private fun Text(
    text: UiText,
    modifier: Modifier = Modifier,
    tag: String,
) {
    Box(modifier = modifier.testTag(tag).padding(all = 8.dp)) { Label(MissionControlStrings.format(text)) }
}

/** The list takes this share of the middle column while the preview is open; the preview takes the rest. */
private const val LIST_SHARE = 0.6f

/** Start and last activity in the details panel, in the zone of the Mac. */
private val timeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
