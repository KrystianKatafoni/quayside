package dev.quayside.missioncontrol.ui

import androidx.compose.ui.graphics.Color

/**
 * Colors of the dialog (NFR-6). Text has contrast of at least 4.5:1 on [panel] (primary also on [field] and
 * [button]), the focus frame of at least 3:1 on all three. `Colors.secondary` and the error tone do not reach 4.5:1 on
 * the panel and the accent frame only 2.5:1, so the dialog uses lighter variants; [secondary] and [error] are for the
 * panel only.
 */
internal object NewAgentColors {
    val panel = Colors.panel
    val field = Colors.selection
    val button = Colors.button
    val primary = Colors.text
    val secondary = Color(color = 0xFFAEAEB2)
    val error = Color(color = 0xFFFF6961)
    val warning = Colors.of(Tone.WARN)
    val focusBorder = Color(color = 0xFF64D2FF)
}
