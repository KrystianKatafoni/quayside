package dev.quayside.core.designsystem

/** Target size of [AmIconButton]: COMPACT 28 dp, REGULAR 34 dp, LARGE 44 dp; icon 16 / 18 / 20 dp. */
public enum class IconButtonSize { COMPACT, REGULAR, LARGE }

/** Look of [AmIconButton]: GHOST transparent, OUTLINED raised, SELECTED sel, DANGER err outline. */
public enum class IconButtonStyle { GHOST, OUTLINED, SELECTED, DANGER }

/**
 * Options of [AmIconButton]. [focusable] false: never takes focus, so Space and Enter reach window shortcuts;
 * no focus frame and no reserved 4 dp. [tone] colours the icon (ignored by DANGER: err); disabled = content.dim.
 * [badge] non-null: counter circle (warn, warnInk) on the top-right corner, decorative - put the count in the
 * label too ("Notifications, 2 new").
 */
public data class IconButtonOptions(
    val enabled: Boolean = true,
    val focusable: Boolean = true,
    val tone: StatusTone = StatusTone.NEUTRAL,
    val size: IconButtonSize = IconButtonSize.REGULAR,
    val style: IconButtonStyle = IconButtonStyle.GHOST,
    val badge: String? = null,
)
