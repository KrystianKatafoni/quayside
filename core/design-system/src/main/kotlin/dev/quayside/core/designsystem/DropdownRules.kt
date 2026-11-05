package dev.quayside.core.designsystem

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

// Pure rules of AmDropdown (AM-817): where the focus starts, how it moves and where the list is placed.

/** The index the open list focuses: [selected], or 0 when it is outside the list; -1 for an empty list. */
internal fun openAt(
    selected: Int,
    size: Int,
): Int =
    when {
        size <= 0 -> -1
        selected in 0 until size -> selected
        else -> 0
    }

/** The focus after a key: [at] moved by [by], stopped at the ends (never wraps). */
internal fun dropdownStep(
    at: Int,
    by: Int,
    size: Int,
): Int = (at + by).coerceIn(minimumValue = 0, maximumValue = (size - 1).coerceAtLeast(minimumValue = 0))

/**
 * Where the list goes: under the field, over it when there is no room under, kept inside the window. With no room
 * on either side it goes under, moved up to the bottom edge of the window. [anchor] is the field in window
 * coordinates; the list is left aligned with it, moved left when it would cross the right edge, never past 0.
 */
internal fun dropdownPosition(
    anchor: IntRect,
    window: IntSize,
    popup: IntSize,
    gap: Int,
): IntOffset {
    val x = anchor.left.coerceIn(minimumValue = 0, maximumValue = (window.width - popup.width).coerceAtLeast(0))
    val under = anchor.bottom + gap
    val over = anchor.top - gap - popup.height
    val y =
        when {
            under + popup.height <= window.height -> under
            over >= 0 -> over
            else -> (window.height - popup.height).coerceAtLeast(minimumValue = 0)
        }
    return IntOffset(x, y)
}
