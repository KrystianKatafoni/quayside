package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba

/** Token colour of a tone: warn, work, ok, err, and muted for DIM (docs/plans/AM-806.md, table 1). */
internal fun Tone.color(tokens: ColorTokens): Rgba =
    when (this) {
        Tone.WARN -> tokens.status.warn
        Tone.WORK -> tokens.status.work
        Tone.OK -> tokens.status.ok
        Tone.ERR -> tokens.status.err
        Tone.DIM -> tokens.content.muted
    }

/** The colour of this tone in the current theme. Audited at 4.5:1 (plex) on bg, panel, sel and warnBg only. */
@Composable
internal fun Tone.tint(): Color = color(AmTheme.colors).toColor()
