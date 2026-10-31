package dev.quayside.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quayside.terminal.api.SearchDirection
import dev.quayside.terminal.api.SearchResult
import javax.swing.SwingUtilities

private val statusColor = TerminalColors.secondary
private val toggleShape = RoundedCornerShape(size = 4.dp)
private val barShape = RoundedCornerShape(size = 6.dp)

/**
 * The search bar of [tab]'s pane in its top right corner; draws nothing unless the open bar belongs to [tab].
 * Closing (Escape or the × button) returns the focus to the widget of [tab] (plan AM-504, decision 5).
 */
@Composable
internal fun BoxScope.SearchBarHost(
    tab: TerminalTab,
    search: TerminalSearchController,
) {
    val bar by search.state.collectAsState()
    val shown = bar?.takeIf { it.tab == tab.id } ?: return
    val close = {
        search.close()
        SwingUtilities.invokeLater { tab.session.component().requestFocusInWindow() }
    }
    SearchBar(shown, search, close, Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 16.dp))
}

/** Field, hit counter, Aa and .* toggles, up and down arrows and close; the pieces never take the focus. */
@Composable
private fun SearchBar(
    state: SearchBarState,
    search: TerminalSearchController,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier =
            modifier
                .testTag(TerminalTags.SEARCH_BAR)
                .background(color = TerminalColors.panel, shape = barShape)
                .border(width = 1.dp, color = TerminalColors.selection, shape = barShape)
                .padding(all = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchField(state, search, onClose, Modifier.width(width = 180.dp))
        SearchStatus(state)
        SearchToggle(TerminalTags.SEARCH_CASE, glyph = "Aa", state.query.matchCase, search::toggleCase)
        SearchToggle(TerminalTags.SEARCH_REGEX, glyph = ".*", state.query.regex, search::toggleRegex)
        GlyphButton(TerminalTags.SEARCH_UP, glyph = "↑") { search.step(SearchDirection.UP) }
        GlyphButton(TerminalTags.SEARCH_DOWN, glyph = "↓") { search.step(SearchDirection.DOWN) }
        GlyphButton(TerminalTags.SEARCH_CLOSE, glyph = "×", onClose)
    }
}

/** The text field; takes the focus and selects its text whenever [SearchBarState.focusRequest] grows. */
@Composable
private fun SearchField(
    state: SearchBarState,
    search: TerminalSearchController,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    var value by remember { mutableStateOf(TextFieldValue(state.query.text)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        focus.requestFocus()
        value = value.copy(selection = TextRange(start = 0, end = value.text.length))
    }
    BasicTextField(
        value = value,
        onValueChange = {
            value = it
            if (it.text != state.query.text) search.type(it.text)
        },
        modifier = modifier.focusRequester(focus).searchKeys(search, onClose).testTag(TerminalTags.SEARCH_FIELD),
        singleLine = true,
        textStyle = TextStyle(color = TerminalColors.text, fontSize = 13.sp),
        cursorBrush = SolidColor(TerminalColors.text),
    )
}

/** Enter and Shift Enter step, Escape closes, ⌘F selects the text again; handled before the field sees the key. */
private fun Modifier.searchKeys(
    search: TerminalSearchController,
    onClose: () -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        val action = searchKeyAction(event)
        when (action) {
            SearchKeyAction.CLOSE -> onClose()
            SearchKeyAction.STEP_UP -> search.step(SearchDirection.UP)
            SearchKeyAction.STEP_DOWN -> search.step(SearchDirection.DOWN)
            SearchKeyAction.REOPEN -> search.open()
            null -> Unit
        }
        action != null
    }

private enum class SearchKeyAction { CLOSE, STEP_UP, STEP_DOWN, REOPEN }

/** Maps a key event to the search action it triggers; `null` when the field should handle the key itself. */
private fun searchKeyAction(event: KeyEvent): SearchKeyAction? =
    when {
        event.type != KeyEventType.KeyDown -> {
            null
        }

        event.key == Key.Escape -> {
            SearchKeyAction.CLOSE
        }

        event.key == Key.Enter || event.key == Key.NumPadEnter -> {
            if (event.isShiftPressed) SearchKeyAction.STEP_DOWN else SearchKeyAction.STEP_UP
        }

        event.isMetaPressed && event.key == Key.F -> {
            SearchKeyAction.REOPEN
        }

        else -> {
            null
        }
    }

/** Counter `3/12` (`3/10000+` when limited), the fixed no-hits text, or the invalid-pattern text. */
@Composable
private fun SearchStatus(state: SearchBarState) {
    when {
        state.invalid -> {
            Label(
                TerminalStrings["search.invalid"],
                Modifier.testTag(TerminalTags.SEARCH_INVALID),
                TerminalColors.error,
            )
        }

        state.result.count > 0 -> {
            Label(counterText(state.result), Modifier.testTag(TerminalTags.SEARCH_COUNTER), statusColor)
        }

        state.query.text.isNotEmpty() -> {
            Label(TerminalStrings["search.noHits"], Modifier.testTag(TerminalTags.SEARCH_NO_HITS), statusColor)
        }
    }
}

private fun counterText(result: SearchResult): String =
    "${(result.current ?: 0) + 1}/${result.count}${if (result.limited) "+" else ""}"

/** A text toggle such as Aa; [on] is also its `selected` semantics. */
@Composable
private fun SearchToggle(
    tag: String,
    glyph: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .testTag(tag)
            .semantics { selected = on }
            .focusProperties { canFocus = false }
            .background(color = if (on) TerminalColors.accent else Color.Transparent, shape = toggleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Label(text = glyph, mono = true)
    }
}
