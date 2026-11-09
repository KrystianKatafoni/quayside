@file:Suppress("MatchingDeclarationName") // SplitTags plus the composables of the split

package dev.quayside.app

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor

/** Semantics tags of the split for UI tests. */
internal object SplitTags {
    const val HANDLE = "app.split.handle"
}

private val HANDLE_HEIGHT: Dp = 6.dp

/**
 * What the last measurement found: the space left for Mission Control and the panel without the handle (read by
 * drags), and whether the panel has anything to show (the handle exists only then).
 */
private class Measured {
    var available: Dp = 0.dp
    var panelShown: Boolean by mutableStateOf(false)
}

/**
 * Mission Control over the terminal [panel] with a draggable handle between them (FR-T2). The panel height is the
 * remembered height of [pane] limited by [paneHeight] at every size of the window; without [pane] it is
 * [DEFAULT_TERMINAL_HEIGHT] and there is no handle. The handle is drawn only while the panel has something to show.
 */
@Composable
internal fun TerminalSplit(
    missionControl: @Composable (Modifier) -> Unit,
    panel: (@Composable (Modifier) -> Unit)?,
    pane: TerminalPaneHeight?,
    modifier: Modifier = Modifier,
) {
    val measured = remember { Measured() }
    val preferred = pane?.preferred?.collectAsState()?.value ?: DEFAULT_TERMINAL_HEIGHT
    val contents: List<@Composable () -> Unit> =
        listOf(
            { missionControl(Modifier.fillMaxWidth()) },
            { if (pane != null && measured.panelShown) SplitHandle(pane, measured) },
            { panel?.invoke(Modifier.fillMaxWidth()) },
        )
    Layout(contents, modifier) { parts, constraints ->
        splitLayout(parts, constraints, preferred, measured)
    }
}

/** Measures and places the three parts; the handle exists only with a panel that has something to measure. */
private fun MeasureScope.splitLayout(
    parts: List<List<Measurable>>,
    constraints: Constraints,
    preferred: Dp,
    measured: Measured,
): MeasureResult {
    val (mission, handle, terminal) = parts
    val handleHeight =
        if (terminal.isEmpty() ||
            handle.isEmpty()
        ) {
            0
        } else {
            HANDLE_HEIGHT.roundToPx().coerceAtMost(constraints.maxHeight)
        }
    val available = (constraints.maxHeight - handleHeight).coerceAtLeast(0)
    measured.available = available.toDp()
    measured.panelShown = terminal.isNotEmpty()
    val paneHeight = if (terminal.isEmpty()) 0 else paneHeight(preferred, available.toDp()).roundToPx()
    val fixed = { height: Int -> Constraints.fixed(constraints.maxWidth, height) }
    val missions = mission.map { it.measure(fixed(available - paneHeight)) }
    val handles = if (handleHeight > 0) handle.map { it.measure(fixed(handleHeight)) } else emptyList()
    val panels = terminal.map { it.measure(fixed(paneHeight)) }
    return layout(constraints.maxWidth, constraints.maxHeight) {
        missions.forEach { it.place(0, 0) }
        handles.forEach { it.place(0, available - paneHeight) }
        panels.forEach { it.place(0, constraints.maxHeight - paneHeight) }
    }
}

/**
 * The transparent strip between Mission Control and the panel. It is a sibling of the panel, not inside it, so no
 * Swing widget covers it. It takes no focus (no `clickable`), so Space and Enter stay with the window keys.
 */
@Composable
private fun SplitHandle(
    pane: TerminalPaneHeight,
    measured: Measured,
) {
    val density = LocalDensity.current
    val state = rememberDraggableState { pixels -> pane.drag(with(density) { pixels.toDp() }, measured.available) }
    Box(
        Modifier
            .fillMaxWidth()
            .testTag(SplitTags.HANDLE)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
            .focusProperties { canFocus = false }
            // Only for the Focused semantics property (tests assert it is false); canFocus = false keeps Tab off.
            .focusable()
            .draggable(state, Orientation.Vertical, onDragStopped = { pane.dragStopped() }),
    )
}
