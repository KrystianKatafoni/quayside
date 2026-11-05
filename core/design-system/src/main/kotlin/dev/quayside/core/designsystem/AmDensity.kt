package dev.quayside.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How tight the session list is (docs/08); the default keeps the AM-809 measures. */
public enum class AmDensity { COMPACT, COMFORTABLE }

/**
 * Measures of one list row: [tile] icon tile, [vertical] padding above and below, [twoLines] summary under the
 * name.
 */
@Immutable
public data class ListMetrics(
    val tile: Dp,
    val vertical: Dp,
    val twoLines: Boolean,
)

/** COMFORTABLE 38 dp, 8 dp, two lines; COMPACT 28 dp, 4 dp, one line. */
@Suppress("MagicNumber") // the measures of docs/08
public fun AmDensity.listMetrics(): ListMetrics =
    when (this) {
        AmDensity.COMFORTABLE -> ListMetrics(tile = 38.dp, vertical = 8.dp, twoLines = true)
        AmDensity.COMPACT -> ListMetrics(tile = 28.dp, vertical = 4.dp, twoLines = false)
    }
