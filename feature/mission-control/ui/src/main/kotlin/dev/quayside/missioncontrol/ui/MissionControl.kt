package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.TextRole

/**
 * The centre of the window: header with the filter segments, session list, key hints and details panel; stateless
 * apart from [controller]. The status bar is [StatusBar], drawn by `app` (AM-816). [handlers] say what `app` does on
 * Attach, Reply (AM-511) and a click on a subfolder row (AM-307); they do nothing by default.
 */
@Composable
public fun MissionControl(
    controller: MissionControlController,
    modifier: Modifier = Modifier,
    handlers: MissionControlHandlers = MissionControlHandlers(),
) {
    val state by controller.state.collectAsState()
    val onEvent: (MissionControlEvent) -> Unit = { event ->
        when (event) {
            MissionControlEvent.Attach -> handlers.onAttach()
            MissionControlEvent.Reply -> handlers.onReply()
            is MissionControlEvent.OpenSubfolder -> handlers.onOpenSubfolder(event.focus)
            else -> controller.onEvent(event)
        }
    }
    CompositionLocalProvider(LocalKeyCaps provides handlers.keyCaps) {
        Column(modifier = modifier.fillMaxSize().background(color = bgColor()).testTag(MissionControlTags.ROOT)) {
            Box(modifier = Modifier.weight(weight = 1f).fillMaxWidth()) { ScreenContent(state, onEvent) }
        }
        state.dialog?.let { SessionDialogView(it, onEvent) }
    }
}

@Composable
private fun ScreenContent(
    state: MissionControlUiState,
    onEvent: (MissionControlEvent) -> Unit,
) {
    when (val screen = state.screen) {
        Screen.NoProjects -> Empty("empty.noProjects", "empty.noProjects.hint")
        Screen.NoSelection -> Empty("empty.noSelection", hint = null)
        is Screen.Project -> ProjectPage(screen, state.preview, onEvent)
        is Screen.Global -> GlobalPage(screen, state.preview, onEvent)
    }
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
    CompositionLocalProvider(LocalProjectDir provides page.header.path) {
        SessionPage(page.banner, page.loading, page.rows, page.details, preview, onEvent) {
            ProjectHeaderView(page.header, page.filter, onEvent)
            StatCards(page.stats)
            page.overview?.let { GroupOverviewView(it, onEvent) }
        }
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
            if (loading) {
                Text(UiText("list.loading"), tag = MissionControlTags.LOADING)
            } else {
                val listWeight = if (preview == null) 1f else LIST_SHARE
                SessionRows(rows, details, onEvent, Modifier.weight(weight = listWeight))
            }
            preview?.let { SessionPreviewView(it, onEvent, Modifier.weight(weight = 1f - LIST_SHARE)) }
            KeyHintsBar()
        }
        Details(details, rows.firstOrNull { it.id == details?.id }?.age, onEvent)
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
