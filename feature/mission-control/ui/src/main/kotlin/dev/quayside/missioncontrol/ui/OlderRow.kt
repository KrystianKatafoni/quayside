package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.ButtonOptions

/**
 * The last row of the list when the count limit cut off [count] disk sessions (FR-P9): "jeszcze N starszych" and the
 * link "Zmień limit", which `app` answers by opening the window "Stare sesje z dysku". The link never takes focus,
 * so Space and Enter reach the window shortcuts.
 */
@Composable
internal fun OlderRow(
    count: Int,
    onEvent: (MissionControlEvent) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(
                    HistoryTags.OLDER_ROW,
                ).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmText(MissionControlStrings.plural("older.row", count), color = mutedColor())
        AmButton(
            MissionControlStrings["older.change"],
            { onEvent(MissionControlEvent.ChangeHistoryLimit) },
            Modifier.testTag(HistoryTags.OLDER_CHANGE),
            ButtonOptions(focusable = false),
        )
    }
}
