package dev.quayside.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.amFocusFrame
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind

private val kindWidth = 200.dp
private val channelWidth = 90.dp

/** The matrix of every kind (rows) and channel (columns); each cell is a checkbox described as `<kind> – <channel>`. */
@Composable
internal fun ChannelMatrix(
    draft: NotificationDraft,
    controller: NotificationSettingsController,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        AmText(SettingsStrings["settings.matrix"])
        Row {
            Box(Modifier.width(kindWidth))
            NotificationChannel.entries.forEach { channel ->
                Box(Modifier.width(channelWidth)) {
                    AmText(
                        SettingsStrings.of(channel),
                        color = mutedColor(),
                    )
                }
            }
        }
        NotificationKind.entries.forEach { kind ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmText(SettingsStrings.of(kind), Modifier.width(kindWidth))
                NotificationChannel.entries.forEach { channel ->
                    val on = channel in draft.matrix[kind].orEmpty()
                    MatrixCell(kind, channel, on) { controller.edit { it.toggled(kind, channel) } }
                }
            }
        }
    }
}

@Composable
private fun MatrixCell(
    kind: NotificationKind,
    channel: NotificationChannel,
    on: Boolean,
    onToggle: () -> Unit,
) {
    Box(Modifier.width(channelWidth)) {
        Box(
            Modifier
                .testTag(SettingsTags.cell(kind, channel))
                .amFocusFrame()
                .semantics { contentDescription = SettingsStrings.cellLabel(kind, channel) }
                .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() }),
        ) {
            CheckMark(on)
        }
    }
}

private fun NotificationDraft.toggled(
    kind: NotificationKind,
    channel: NotificationChannel,
): NotificationDraft {
    val channels = matrix[kind].orEmpty()
    return copy(matrix = matrix + (kind to if (channel in channels) channels - channel else channels + channel))
}
