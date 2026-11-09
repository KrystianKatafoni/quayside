package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.quayside.core.designsystem.AmIconButton
import dev.quayside.core.designsystem.IconButtonOptions
import dev.quayside.core.designsystem.IconButtonStyle
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone

/**
 * Bell with the count of the "Czeka na mnie" view as badge; a click shows that view, then calls [onOpen]. The count is
 * in the label ("Czeka na mnie: 3"), so the badge is decorative. Never takes focus.
 */
@Composable
public fun AlertsBell(
    controller: MissionControlController,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val count = state.views.firstOrNull { it.view == GlobalView.WAITING }?.count ?: 0
    val label = MissionControlStrings.format(UiText("view.count", listOf(GlobalView.WAITING.title(), "$count")))
    AmIconButton(
        IconKind.NOTIFICATIONS,
        label,
        {
            controller.onIntent(MissionControlIntent.ShowView(GlobalView.WAITING))
            onOpen()
        },
        modifier.testTag(MissionControlTags.ALERTS_BELL),
        IconButtonOptions(
            focusable = false,
            tone = if (count > 0) StatusTone.WARN else StatusTone.NEUTRAL,
            style = IconButtonStyle.OUTLINED,
            badge = count.takeIf { it > 0 }?.toString(),
        ),
    )
}
