package dev.quayside.notifications.ui

import dev.quayside.notifications.api.PopupModel
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupStyle

/** Sizes of the popup window in dp (AWT points). Cards have fixed heights, so the window height is exact. */
internal object PopupMetrics {
    const val CARD_WIDTH = 360
    const val PADDING = 8
    const val GAP = 8
    const val MARGIN = 16
    const val WINDOW_WIDTH = CARD_WIDTH + 2 * PADDING
    private const val FULL = 116
    private const val COMPACT = 68
    private const val COLLAPSED = 32

    /** Height of the card of [popup] in [style]. */
    fun cardHeight(
        popup: PopupModel,
        style: PopupStyle,
    ): Int =
        when {
            popup.collapsed -> COLLAPSED
            style == PopupStyle.COMPACT -> COMPACT
            else -> FULL
        }

    /** Height of the window around the cards of [popups]. */
    fun windowHeight(
        popups: List<PopupModel>,
        style: PopupStyle,
    ): Int = popups.sumOf { cardHeight(it, style) } + GAP * (popups.size - 1).coerceAtLeast(0) + 2 * PADDING
}

/** The usable part of a screen (without the menu bar and the Dock), in dp. */
internal data class ScreenArea(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** Top left corner of the popup window in dp. */
internal data class Origin(
    val x: Int,
    val y: Int,
)

/** Where a window of [width] × [height] goes on [area] for [position], [margin] away from the edges. */
internal fun popupOrigin(
    position: PopupPosition,
    area: ScreenArea,
    width: Int,
    height: Int,
    margin: Int = PopupMetrics.MARGIN,
): Origin {
    val left = position == PopupPosition.TOP_LEFT || position == PopupPosition.BOTTOM_LEFT
    val centre = position == PopupPosition.TOP_CENTER || position == PopupPosition.BOTTOM_CENTER
    val top = position.name.startsWith("TOP")
    val x =
        when {
            left -> area.x + margin
            centre -> area.x + (area.width - width) / 2
            else -> area.x + area.width - width - margin
        }
    val y = if (top) area.y + margin else area.y + area.height - height - margin
    return Origin(x.coerceAtLeast(area.x), y.coerceAtLeast(area.y))
}
