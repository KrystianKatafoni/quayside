package dev.quayside.core.designsystem

/**
 * Options of [AmButton]. [icon] 16 dp before the label, in the label colour. [shortcut] (e.g. "⌘N", "r") after the
 * label in CODE, label colour, no frame; read with the label as one node. [focusable] false: never takes focus,
 * so Space and Enter reach window shortcuts; no focus frame, no reserved 4 dp (as [IconButtonOptions]).
 */
public data class ButtonOptions(
    val kind: ButtonKind = ButtonKind.SECONDARY,
    val enabled: Boolean = true,
    val focusable: Boolean = true,
    val icon: IconKind? = null,
    val shortcut: String? = null,
)
