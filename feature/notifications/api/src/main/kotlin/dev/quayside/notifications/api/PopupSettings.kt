package dev.quayside.notifications.api

/** Where the popups appear on the screen (FR-N3): top or bottom row × left, centre, right. */
public enum class PopupPosition {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
}

/** How long a popup stays (FR-N3). */
public enum class PopupDuration {
    /** Questions and permission requests stay until closed, the rest goes after a few seconds (docs/07 default). */
    QUESTIONS_STAY,

    /** Every popup goes after a few seconds. */
    ALWAYS_TIMED,

    /** Every popup stays until closed. */
    ALWAYS_STAY,
}

/** How much a popup shows (FR-N3). */
public enum class PopupStyle { DETAILED, COMPACT }

/**
 * How popups look and behave (FR-N3, docs/07 defaults).
 *
 * @property maxVisible popups shown in full at once, 1..[MAX_VISIBLE_LIMIT]; older ones shrink to a compact line.
 */
public data class PopupSettings(
    val position: PopupPosition = PopupPosition.BOTTOM_RIGHT,
    val duration: PopupDuration = PopupDuration.QUESTIONS_STAY,
    val maxVisible: Int = DEFAULT_MAX_VISIBLE,
    val style: PopupStyle = PopupStyle.DETAILED,
) {
    init {
        require(maxVisible in 1..MAX_VISIBLE_LIMIT) { "maxVisible out of range" }
    }

    public companion object {
        public const val DEFAULT_MAX_VISIBLE: Int = 3
        public const val MAX_VISIBLE_LIMIT: Int = 10
    }
}
