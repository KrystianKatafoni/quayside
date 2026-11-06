package dev.quayside.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmIcon
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toColor
import dev.quayside.notifications.api.NotificationSettings

/**
 * Chip of the window top bar: the VOICE icon and the voice name ("Głos systemowy" for the system voice, "Głos wył."
 * while the voice is off) on a raised field. A click calls [onClick]. Never takes focus, so Space and Enter go on to
 * the window.
 */
@Composable
public fun VoiceChip(
    settings: NotificationSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = AmTheme.shape
    Row(
        modifier =
            modifier
                .focusProperties { canFocus = false }
                .clip(shape.corner)
                .background(
                    AmTheme.colors.surfaces.raised
                        .toColor(),
                ).border(
                    shape.borderWidth,
                    AmTheme.colors.surfaces.border2
                        .toColor(),
                    shape.corner,
                ).clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AmIcon(IconKind.VOICE, null, Modifier.size(ICON_SIZE))
        AmText(voiceLabel(settings), role = TextRole.CAPTION)
    }
}

private val ICON_SIZE = 16.dp

private fun voiceLabel(settings: NotificationSettings): String =
    when {
        !settings.voice -> SettingsStrings["chip.voiceOff"]
        else -> settings.speech.voice?.name ?: SettingsStrings["voice.system"]
    }

/**
 * The status bar text of the notifications, e.g. "głos: wł. · popup: prawy dół"; "popup: wył." while popups are off,
 * whatever the position.
 */
public fun notificationStatus(settings: NotificationSettings): String {
    val voice = SettingsStrings[if (settings.voice) "status.voice.on" else "status.voice.off"]
    val popup =
        if (settings.popups) {
            SettingsStrings["status.popup"].replace(
                "{position}",
                SettingsStrings.option("statusPosition", settings.popup.position),
            )
        } else {
            SettingsStrings["status.popup.off"]
        }
    return "$voice · $popup"
}
