package dev.quayside.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmSearchField
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toColor
import dev.quayside.missioncontrol.ui.AlertsBell
import dev.quayside.missioncontrol.ui.StatusBar
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.ui.VoiceChip
import dev.quayside.notifications.ui.notificationStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * What the top bar and the status bar of the window need besides the controllers (AM-816): [onNewAgent] and whether it
 * is [newAgentEnabled] (as the menu item), the [notifications] behind the voice chip and the status text, and the
 * [supervisor] version of Claude Code (null while unknown or without Claude Code).
 */
internal class WindowChrome(
    val onNewAgent: () -> Unit = {},
    val newAgentEnabled: Boolean = true,
    val notifications: NotificationChrome? = null,
    val supervisor: String? = null,
)

/** The notification settings of the window and what a click on the voice chip does. */
internal class NotificationChrome(
    val settings: StateFlow<NotificationSettings>,
    val onOpen: () -> Unit,
)

/** Semantics tags of the top bar of the window (AM-816) for UI tests. */
internal object TopBarTags {
    const val BAR = "app.topBar"
    const val CRUMBS = "app.topBar.crumbs"
    const val SEARCH = "app.topBar.search"
    const val VOICE = "app.topBar.voice"
}

/**
 * The bar over both layouts, under the native title bar: crumbs, the search field (opens the palette), the voice
 * chip, the bell of the alerts and "Nowy agent" at the right edge. Nothing in it takes focus, so the keys of the
 * window keep working. [onBell] runs after the bell has shown the "Czeka na mnie" view.
 */
@Composable
internal fun WindowTopBar(
    controllers: AppControllers,
    chrome: WindowChrome,
    crumbs: Crumbs,
    onBell: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .barSurface()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag(TopBarTags.BAR),
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Breadcrumbs(crumbs, Modifier.weight(weight = 1f))
        AmSearchField(
            Strings["topBar.search"],
            keyCapOf(AppShortcuts.PALETTE),
            controllers.palette::open,
            Modifier.width(SEARCH_WIDTH).testTag(TopBarTags.SEARCH),
        )
        Row(
            modifier = Modifier.weight(weight = 1f),
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp, alignment = Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) { TopBarActions(controllers, chrome, onBell) }
    }
}

/** The panel fill with the theme border under the bar. */
@Composable
private fun Modifier.barSurface(): Modifier {
    val border =
        AmTheme.colors.surfaces.border
            .toColor()
    val width = AmTheme.shape.borderWidth
    return background(
        AmTheme.colors.surfaces.panel
            .toColor(),
    ).drawBehind {
        val y = size.height - width.toPx() / 2
        drawLine(border, Offset(0f, y), Offset(size.width, y), width.toPx())
    }
}

private val SEARCH_WIDTH = 360.dp

@Composable
private fun TopBarActions(
    controllers: AppControllers,
    chrome: WindowChrome,
    onBell: () -> Unit,
) {
    chrome.notifications?.let { notifications ->
        val settings by notifications.settings.collectAsState()
        VoiceChip(settings, notifications.onOpen, Modifier.testTag(TopBarTags.VOICE))
    }
    AlertsBell(controllers.missionControl, onBell)
}

/**
 * The status bar of the window in both layouts (AM-816): the mode badge, project, counts and the notification status
 * from [chrome], then the freshness and "supervisor X".
 */
@Composable
internal fun WindowStatusBar(
    controllers: AppControllers,
    chrome: WindowChrome,
    terminalMode: Boolean,
    modifier: Modifier = Modifier,
) {
    StatusBar(controllers.missionControl, terminalMode, chrome.supervisor, modifier) {
        chrome.notifications?.let { NotificationStatusText(it) }
    }
}

@Composable
private fun RowScope.NotificationStatusText(notifications: NotificationChrome) {
    val settings by notifications.settings.collectAsState()
    AmText(notificationStatus(settings), role = TextRole.CAPTION)
}
