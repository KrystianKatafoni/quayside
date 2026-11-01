package dev.quayside.notifications.ui

import dev.quayside.notifications.api.NotificationCenter
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.NotificationSettingsRepo
import dev.quayside.notifications.api.PopupAction
import dev.quayside.notifications.api.PopupId
import dev.quayside.notifications.api.PopupModel
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the popup window draws: the [popups] (newest first) and how ([position], [style]). */
public data class PopupUiState(
    val popups: List<PopupModel>,
    val position: PopupPosition,
    val style: PopupStyle,
)

/**
 * State holder of the popup window (FR-N3): joins the popups of [center] with the popup settings and forwards the
 * user's actions to the center. Lives in [scope] as long as the app; holds no state of its own.
 */
public class PopupController(
    private val center: NotificationCenter,
    settings: NotificationSettingsRepo,
    private val scope: CoroutineScope,
) {
    private val join = { popups: List<PopupModel>, all: NotificationSettings ->
        PopupUiState(popups, all.popup.position, all.popup.style)
    }

    /** The current content of the window; empty [PopupUiState.popups] means the window is hidden. */
    public val state: StateFlow<PopupUiState> =
        combine(center.popups, settings.settings, join)
            .stateIn(scope, SharingStarted.Eagerly, join(center.popups.value, settings.settings.value))

    /** The user chose [action] on popup [id]; runs in the controller's scope. */
    public fun onAction(
        id: PopupId,
        action: PopupAction,
    ) {
        scope.launch { center.onAction(id, action) }
    }
}
