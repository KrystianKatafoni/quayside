package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * The context menu of a session row (AM-511): the [controls] of the selected session, shown while [open]. The
 * state lives in the page ([setOpen] changes it); the menu draws nothing without [controls], e.g. in the step
 * between a right click on another row and the selection it causes.
 */
internal class RowMenu(
    val controls: List<SessionControl>,
    val open: Boolean,
    val setOpen: (Boolean) -> Unit,
)

/** The "⋯" button of a row; the popup of [menu] hangs under it. Never takes focus (see [SessionRowView]). */
@Composable
internal fun RowMenuButton(
    tag: String,
    menu: RowMenu,
    onPick: (MissionControlEvent) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .testTag(tag)
                .focusProperties { canFocus = false }
                .clickable(role = Role.Button) { menu.setOpen(true) }
                .padding(horizontal = 8.dp),
    ) {
        Label(MissionControlStrings["row.menu"], Colors.secondary)
        if (menu.open && menu.controls.isNotEmpty()) RowMenuPopup(menu, onPick)
    }
}

/** Escape and a click outside close the menu; picking an item closes it and then sends the item's event. */
@Composable
private fun RowMenuPopup(
    menu: RowMenu,
    onPick: (MissionControlEvent) -> Unit,
) {
    val focus = remember { FocusRequester() }
    Popup(
        popupPositionProvider = BelowAnchor,
        onDismissRequest = { menu.setOpen(false) },
        properties = PopupProperties(focusable = true),
    ) {
        // Inside the popup: the modifier is attached only once its content is composed (like the dialogs).
        LaunchedEffect(Unit) { focus.requestFocus() }
        Column(
            modifier =
                Modifier
                    .width(width = 200.dp)
                    .background(color = Colors.panel, shape = RoundedCornerShape(size = 8.dp))
                    .padding(vertical = 4.dp)
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { event -> closesOn(event).also { if (it) menu.setOpen(false) } }
                    .testTag(MissionControlTags.ROW_MENU),
        ) {
            menu.controls.forEach { control ->
                MenuItem(control) {
                    menu.setOpen(false)
                    onPick(control.event)
                }
            }
        }
    }
}

private fun closesOn(event: KeyEvent): Boolean = event.type == KeyEventType.KeyDown && event.key == Key.Escape

@Composable
private fun MenuItem(
    control: SessionControl,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .testTag(MissionControlTags.menuItem(control.id))
                .focusProperties { canFocus = false }
                .clickable(role = Role.Button, onClick = onClick)
                .width(width = 200.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Label(MissionControlStrings[control.labelKey])
    }
}

/** Under the anchor with the right edges aligned; above it when there is no room below. */
private object BelowAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceAtLeast(minimumValue = 0)
        val fitsBelow = anchorBounds.bottom + popupContentSize.height <= windowSize.height
        val y = if (fitsBelow) anchorBounds.bottom else (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/**
 * Mouse paths of a row that a plain `clickable` has not: [onSecondary] when the secondary button (right click, two
 * fingers on a trackpad) goes down, [onDouble] when a second primary press follows the first within the system's
 * double-tap time. Watches the presses in the initial pass and consumes nothing, so the row's own `clickable` selects
 * at once, with no wait for a possible double click. [key] restarts the watch.
 */
@Composable
internal fun Modifier.onRowMouse(
    key: Any,
    onSecondary: () -> Unit,
    onDouble: () -> Unit,
): Modifier {
    val secondary by rememberUpdatedState(onSecondary)
    val double by rememberUpdatedState(onDouble)
    return pointerInput(key) {
        awaitPointerEventScope {
            var lastPress = NO_PRESS
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press) continue
                when {
                    event.buttons.isSecondaryPressed -> {
                        secondary()
                    }

                    event.buttons.isPrimaryPressed -> {
                        val at = event.changes.first().uptimeMillis
                        val twice = lastPress != NO_PRESS && at - lastPress <= viewConfiguration.doubleTapTimeoutMillis
                        if (twice) double()
                        lastPress = if (twice) NO_PRESS else at
                    }
                }
            }
        }
    }
}

private const val NO_PRESS = -1L
