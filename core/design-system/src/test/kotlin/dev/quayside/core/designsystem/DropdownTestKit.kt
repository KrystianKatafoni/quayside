@file:OptIn(ExperimentalTestApi::class)

package dev.quayside.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/** Tag of the dropdown field in the tests of the component. */
internal const val FIELD = "field"

/** A click on this box is a click outside the field and outside the list. */
internal const val OUTSIDE = "outside"

/** Cold JVM start-up can exceed the default 1 s of `waitUntil` in the first test. */
internal const val DROPDOWN_WAIT_MS = 5_000L

/** Four modes like the permission list of "Nowy agent": the last one is the dangerous one. */
internal val MODES = listOf("Default", "Accept edits", "Plan", "Bypass")

/**
 * A dropdown under test with its surroundings: [picks] records every `onSelect`, [passed] every key that reached the
 * parent unconsumed, [gate] is the modal gate of the window. With [inDialog] the dropdown sits in a focusable popup
 * that handles Escape itself ([dialogEscapes]) and counts its dismiss requests ([dialogDismissals]), like a dialog.
 */
internal class DropdownRig(
    val labels: List<String> = MODES,
    selected: Int = 0,
    val placeholder: String = "",
    val label: String = "Mode",
    private val inDialog: Boolean = false,
) {
    var selected by mutableIntStateOf(selected)
    var enabled by mutableStateOf(true)
    val picks = mutableListOf<Int>()
    val passed = mutableListOf<Key>()
    val gate = AmModalGate()
    var dialogEscapes = 0
    var dialogDismissals = 0

    @Composable
    fun Content() {
        CompositionLocalProvider(LocalAmModalGate provides gate) {
            PlexTheme {
                if (inDialog) {
                    Popup(onDismissRequest = { dialogDismissals++ }, properties = PopupProperties(focusable = true)) {
                        Body(Modifier.onPreviewKeyEvent { event -> escapeOfDialog(event) })
                    }
                } else {
                    Body(Modifier)
                }
            }
        }
    }

    private fun escapeOfDialog(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
        if (escape) dialogEscapes++
        return escape
    }

    @Composable
    private fun Body(modifier: Modifier) {
        Column(
            modifier.onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) passed += event.key
                false
            },
        ) {
            Box(Modifier.testTag(OUTSIDE).size(40.dp).clickable {})
            AmDropdown(
                items = AmDropdownItems(labels, selected, placeholder),
                label = label,
                onSelect = { index ->
                    picks += index
                    selected = index
                },
                modifier = Modifier.testTag(FIELD),
                enabled = enabled,
            )
        }
    }
}

/** Shows [rig]. */
internal fun ComposeUiTest.show(rig: DropdownRig) {
    setContent { rig.Content() }
}

/** Gives the field the focus the way a click or Tab does, without a click. */
internal fun ComposeUiTest.focusField() {
    onNodeWithTag(FIELD).focus()
    waitForIdle()
}

/** Clicks the field (which opens the list) and waits until the list is there. */
internal fun ComposeUiTest.openByClick() {
    onNodeWithTag(FIELD).performClick()
    waitUntilExactlyOneExists(hasTestTag(AmDropdownTags.LIST), DROPDOWN_WAIT_MS)
}

/** Waits until the node with [tag] has the focus. */
internal fun ComposeUiTest.awaitFocus(tag: String) {
    waitUntilExactlyOneExists(hasTestTag(tag) and isFocused(), DROPDOWN_WAIT_MS)
}

/** Waits until the open list is gone. */
internal fun ComposeUiTest.awaitClosed() {
    waitUntilDoesNotExist(hasTestTag(AmDropdownTags.LIST), DROPDOWN_WAIT_MS)
}

/**
 * Presses [key] and waits until the UI is idle. While the list is open (a second root exists) `onRoot` is ambiguous,
 * so the key goes to the list node; otherwise to the field.
 */
internal fun ComposeUiTest.pressKey(key: Key) {
    val listOpen = onAllNodesWithTag(AmDropdownTags.LIST).fetchSemanticsNodes().isNotEmpty()
    onNodeWithTag(if (listOpen) AmDropdownTags.LIST else FIELD).performKeyInput {
        keyDown(key)
        keyUp(key)
    }
    waitForIdle()
}

/** Presses [key] with [modifier] held, in the window. */
internal fun ComposeUiTest.pressWith(
    modifier: Key,
    key: Key,
) {
    onRoot().performKeyInput {
        keyDown(modifier)
        keyDown(key)
        keyUp(key)
        keyUp(modifier)
    }
    waitForIdle()
}

private val KEYS_BY_NAME: Map<String, Key> =
    mapOf(
        "Enter" to Key.Enter,
        "Space" to Key.Spacebar,
        "Down" to Key.DirectionDown,
        "Up" to Key.DirectionUp,
        "Left" to Key.DirectionLeft,
        "Right" to Key.DirectionRight,
        "Escape" to Key.Escape,
        "Tab" to Key.Tab,
        "Home" to Key.MoveHome,
        "End" to Key.MoveEnd,
        "PageUp" to Key.PageUp,
        "PageDown" to Key.PageDown,
        "Meta" to Key.MetaLeft,
        "Ctrl" to Key.CtrlLeft,
        "Alt" to Key.AltLeft,
    )

/** The keys of the tables by name; value classes do not go through `@MethodSource` safely. */
internal fun keyNamed(name: String): Key = checkNotNull(KEYS_BY_NAME[name]) { "no key named $name" }
