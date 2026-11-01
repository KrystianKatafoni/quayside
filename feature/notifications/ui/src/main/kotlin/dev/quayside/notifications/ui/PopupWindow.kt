package dev.quayside.notifications.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState

/**
 * The popup window (FR-N3): undecorated, transparent, always on top and not focusable, so it never takes the focus
 * from a terminal. Shown only while [controller] has popups; sits in the corner of the main screen chosen in the
 * settings. Call it inside `application {}`.
 */
@Composable
public fun PopupWindow(controller: PopupController) {
    val state by controller.state.collectAsState()
    if (state.popups.isEmpty()) return
    val area = currentScreenArea()
    val height = minOf(PopupMetrics.windowHeight(state.popups, state.style), area.height)
    val origin = popupOrigin(state.position, area, PopupMetrics.WINDOW_WIDTH, height)
    val windowState =
        rememberWindowState(
            position = WindowPosition(origin.x.dp, origin.y.dp),
            size = DpSize(PopupMetrics.WINDOW_WIDTH.dp, height.dp),
        )
    LaunchedEffect(origin, height) {
        windowState.position = WindowPosition(origin.x.dp, origin.y.dp)
        windowState.size = DpSize(PopupMetrics.WINDOW_WIDTH.dp, height.dp)
    }
    Window(
        onCloseRequest = {},
        state = windowState,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
    ) {
        PopupStack(state, controller::onAction)
    }
}
