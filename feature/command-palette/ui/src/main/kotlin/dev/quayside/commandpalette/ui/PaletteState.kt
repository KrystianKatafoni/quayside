package dev.quayside.commandpalette.ui

import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandMatcher

/** Palette state kept by the controller; [highlighted] may point past the results until [view] clamps it. */
internal data class PaletteState(
    val open: Boolean = false,
    val query: String = "",
    val group: CommandGroup? = null,
    val highlighted: Int = 0,
)

/** What the composable shows: [results] are filtered and ordered, [highlighted] is in range. */
internal data class PaletteView(
    val open: Boolean,
    val query: String,
    val results: List<Command>,
    val highlighted: Int,
)

/** Input of the palette: typing, keys and clicks. */
internal sealed interface PaletteEvent {
    data class QueryChanged(
        val text: String,
    ) : PaletteEvent

    data object MoveUp : PaletteEvent

    data object MoveDown : PaletteEvent

    data object Confirm : PaletteEvent

    data class Pick(
        val index: Int,
    ) : PaletteEvent

    data object Dismiss : PaletteEvent
}

/** The results for [all]: searched by the query, limited to the group, with the highlight kept in range. */
internal fun PaletteState.view(all: List<Command>): PaletteView {
    val results = CommandMatcher.search(query, all).filter { group == null || it.group == group }
    return PaletteView(open, query, results, highlighted.coerceIn(0, maxOf(results.lastIndex, 0)))
}

/** The next state for [event]; [resultCount] bounds the arrows, which do not wrap. Confirm and Pick change nothing. */
internal fun PaletteState.reduce(
    event: PaletteEvent,
    resultCount: Int,
): PaletteState {
    val last = maxOf(resultCount - 1, 0)
    return when (event) {
        is PaletteEvent.QueryChanged -> copy(query = event.text, highlighted = 0)
        PaletteEvent.MoveDown -> copy(highlighted = (highlighted + 1).coerceAtMost(last))
        PaletteEvent.MoveUp -> copy(highlighted = (highlighted - 1).coerceAtLeast(0))
        PaletteEvent.Dismiss -> PaletteState()
        PaletteEvent.Confirm, is PaletteEvent.Pick -> this
    }
}
