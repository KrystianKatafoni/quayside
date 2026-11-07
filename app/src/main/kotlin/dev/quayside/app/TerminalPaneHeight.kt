package dev.quayside.app

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Default, minimum terminal and minimum Mission Control height of the list mode, in layout dp (after the UI scale). */
internal val DEFAULT_TERMINAL_HEIGHT: Dp = 320.dp
internal val MIN_TERMINAL_HEIGHT: Dp = 120.dp
internal val MIN_MISSION_HEIGHT: Dp = 160.dp

/**
 * The terminal height shown for the remembered [preferred] height when [available] dp are left under the top bar,
 * without the handle: at least [MIN_TERMINAL_HEIGHT], leaving at least [MIN_MISSION_HEIGHT] to Mission Control; when
 * both minimums do not fit, the terminal minimum wins, but never more than [available] (and never below 0).
 */
internal fun paneHeight(
    preferred: Dp,
    available: Dp,
): Dp {
    val most = maxOf(MIN_TERMINAL_HEIGHT, available - MIN_MISSION_HEIGHT)
    return preferred.coerceIn(MIN_TERMINAL_HEIGHT, most).coerceIn(0.dp, maxOf(0.dp, available))
}

/** The new remembered height after a drag by [delta] (positive = down) from the [shown] height: [paneHeight] of it. */
internal fun afterDrag(
    shown: Dp,
    delta: Dp,
    available: Dp,
): Dp = paneHeight(shown - delta, available)

/**
 * The terminal height of one window. [preferred] is the remembered height; drags change it at once, [dragStopped]
 * hands the latest value to [store]. One collector in [scope] saves, so a slow save skips the values in between;
 * nothing is saved before the first drag. A failed save is logged (fixed category) and the height stays.
 */
internal class TerminalPaneHeight(
    initial: Dp,
    private val store: WindowLayoutStore,
    scope: CoroutineScope,
    private val logger: Logger,
) {
    private val height = MutableStateFlow(initial)
    private val toSave = MutableStateFlow<Dp?>(null)

    /** The remembered height; the layout shows [paneHeight] of it. */
    val preferred: StateFlow<Dp> get() = height

    init {
        scope.launch { toSave.filterNotNull().collect(::save) }
    }

    /** Moves the handle by [delta] (positive = down) from the height shown now, with [available] dp to share. */
    fun drag(
        delta: Dp,
        available: Dp,
    ) {
        height.value = afterDrag(paneHeight(height.value, available), delta, available)
    }

    /** The drag ended: the current height goes to the store. */
    fun dragStopped() {
        toSave.value = height.value
    }

    private suspend fun save(dp: Dp) {
        val saved = store.save(WindowLayout(terminalHeight = dp.value))
        if (saved is Outcome.Err) logger.warn("window layout not saved", "error" to saved.error::class.simpleName)
    }
}
