@file:OptIn(ExperimentalTestApi::class)

package dev.quayside.notifications.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmDropdownTags

private const val LIST_WAIT_MS = 5_000L

/** Gives the field the focus the way Tab does; a click on a dropdown field would open its list. */
internal fun SemanticsNodeInteraction.focus() = performSemanticsAction(SemanticsActions.RequestFocus)

/** Waits until a dropdown list is open. */
internal fun ComposeUiTest.awaitListOpen() = waitUntilExactlyOneExists(hasTestTag(AmDropdownTags.LIST), LIST_WAIT_MS)

/** Waits until no dropdown list is open. */
internal fun ComposeUiTest.awaitListGone() = waitUntilDoesNotExist(hasTestTag(AmDropdownTags.LIST), LIST_WAIT_MS)

/** Scrolls the dialog to the dropdown field [tag], clicks it and waits until its list is open. */
internal fun ComposeUiTest.openList(tag: String) {
    waitUntilExactlyOneExists(hasTestTag(tag), LIST_WAIT_MS)
    onNodeWithTag(tag).performScrollTo().performClick()
    awaitListOpen()
}

/** Clicks the option [index] of the open list and waits until the list is gone. */
internal fun ComposeUiTest.clickOption(index: Int) {
    waitUntilExactlyOneExists(hasTestTag(AmDropdownTags.option(index)), LIST_WAIT_MS)
    onNodeWithTag(AmDropdownTags.option(index)).performClick()
    awaitListGone()
}

/** Opens the list of [tag] and clicks its option [index]. */
internal fun ComposeUiTest.pickOption(
    tag: String,
    index: Int,
) {
    openList(tag)
    clickOption(index)
}

/** The text of the option [index] of the open list. */
internal fun ComposeUiTest.optionShows(
    index: Int,
    text: String,
) = waitUntilExactlyOneExists(
    hasTestTag(AmDropdownTags.option(index)) and hasText(text, substring = true),
    LIST_WAIT_MS,
)

/** Sends [key] to the open list, or to the focused field [field] when no list is open. */
internal fun ComposeUiTest.pressKeyOn(
    field: String,
    key: Key,
) {
    val open = onAllNodesWithTag(AmDropdownTags.LIST).fetchSemanticsNodes().isNotEmpty()
    onNodeWithTag(if (open) AmDropdownTags.LIST else field).performKeyInput { pressKey(key) }
    waitForIdle()
}
