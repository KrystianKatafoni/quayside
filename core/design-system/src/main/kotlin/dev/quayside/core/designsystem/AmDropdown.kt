package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * The choices of an [AmDropdown]: [labels] in list order; [selected] is an index into them. A [selected] outside
 * `labels.indices` means none: the field shows [placeholder]. Immutable; never throws.
 */
public data class AmDropdownItems(
    val labels: List<String>,
    val selected: Int,
    val placeholder: String = "",
)

/**
 * A field with the selected label and a down arrow; a click, Enter, Space, Up, Down, Left or Right opens a focusable
 * popup list under it (over it when there is no room), at most 8 rows, then it scrolls. Arrows on the closed field
 * only open the list and never change the value. In the list Up and Down move the focus and never wrap, so a
 * dangerous value at the end is never one key from the first; Enter, Space or a click calls [onSelect] with a new
 * index and closes (the selected option again closes without a call); Escape, Tab or a click outside close with no
 * change. The focus goes back to the field. Keys with Meta, Ctrl or Alt pass through (a window shortcut such as
 * Cmd+Enter works from the closed field; while the list is open it belongs to the list). The open list counts as a
 * modal layer in [LocalAmModalGate]. Focusable: use it in dialogs, not on screens with window shortcuts without
 * Cmd. Semantics: Role.DropdownList, [label] as the content description, the shown label as the state.
 * Disabled ([enabled] false): dim label, does not open, and closes an open list. An empty list does not open.
 */
@Composable
public fun AmDropdown(
    items: AmDropdownItems,
    label: String,
    onSelect: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val state = remember { DropdownState() }
    val canOpen = enabled && items.labels.isNotEmpty()
    LaunchedEffect(enabled) { if (!enabled) state.dismiss() }
    LaunchedEffect(state.closes) { if (state.closes > 0 && enabled) state.field.requestFocus() }
    val shown = items.labels.getOrElse(items.selected) { items.placeholder }
    val ink = dropdownFieldColors(enabled, AmTheme.colors).second.toColor()
    val density = LocalDensity.current
    var width by remember { mutableIntStateOf(0) }
    Box(modifier.onSizeChanged { width = it.width }.dropdownField(state, FieldSpec(label, shown, enabled, canOpen))) {
        AmText(shown, Modifier.padding(end = 20.dp), color = ink)
        AmText("▾", Modifier.align(Alignment.CenterEnd).clearAndSetSemantics {}, color = ink)
        if (state.open && canOpen) {
            val pick = { index: Int -> if (index != items.selected) onSelect(index) }
            DropdownList(items, with(density) { width.toDp() }, pick, state::close)
        }
    }
}

/** What the field shows and whether it works: the [shown] label, the [label] for the screen reader. */
private class FieldSpec(
    val label: String,
    val shown: String,
    val enabled: Boolean,
    val canOpen: Boolean,
)

/** Frame, keys, tap, semantics and look of the closed field. */
@Composable
private fun Modifier.dropdownField(
    state: DropdownState,
    spec: FieldSpec,
): Modifier {
    val fill = dropdownFieldColors(spec.enabled, AmTheme.colors).first
    val shape = AmTheme.shape
    return amFocusFrame()
        .onPreviewKeyEvent { event -> spec.canOpen && state.openedBy(event) }
        .focusRequester(state.field)
        .focusable(spec.enabled)
        .pointerInput(spec.canOpen) { detectTapGestures { if (spec.canOpen) state.open() } }
        .semantics(mergeDescendants = true) { describe(spec.label, spec.shown, spec.enabled) }
        .background(fill.toColor(), shape.corner)
        .border(shape.borderWidth, buttonBorder(AmTheme.colors).toColor(), shape.corner)
        .padding(horizontal = 8.dp, vertical = 4.dp)
}

/** Whether the list is open, and how many times it closed (an effect returns the focus to the field after each). */
@Stable
internal class DropdownState {
    var open: Boolean by mutableStateOf(value = false)
        private set
    var closes: Int by mutableIntStateOf(value = 0)
        private set
    val field = FocusRequester()

    fun open() {
        open = true
    }

    fun close() {
        open = false
        closes += 1
    }

    /** Closes without returning the focus (the field was disabled, so it cannot take it). */
    fun dismiss() {
        open = false
    }

    /** Opens on a key of the closed field; true when it did, i.e. the key is consumed. */
    fun openedBy(event: KeyEvent): Boolean {
        val opens = event.type == KeyEventType.KeyDown && event.key in OPEN_KEYS && !event.hasShortcutModifier()
        if (opens) open()
        return opens
    }
}

private val OPEN_KEYS =
    setOf(
        Key.Enter,
        Key.NumPadEnter,
        Key.Spacebar,
        Key.DirectionDown,
        Key.DirectionUp,
        Key.DirectionLeft,
        Key.DirectionRight,
    )

/** Meta, Ctrl or Alt held: a shortcut of the window or of the dialog, not a key of the control. */
internal fun KeyEvent.hasShortcutModifier(): Boolean = isMetaPressed || isCtrlPressed || isAltPressed

private fun androidx.compose.ui.semantics.SemanticsPropertyReceiver.describe(
    label: String,
    shown: String,
    enabled: Boolean,
) {
    role = Role.DropdownList
    contentDescription = label
    stateDescription = shown
    if (!enabled) disabled()
}

/** Tags of the open list: [LIST] on the group, [option] on each row (only one list is open at a time). */
public object AmDropdownTags {
    /** The list node (a `selectableGroup`). */
    public const val LIST: String = "amDropdown.list"

    /** The row of the option at [index]. */
    public fun option(index: Int): String = "amDropdown.option.$index"
}
