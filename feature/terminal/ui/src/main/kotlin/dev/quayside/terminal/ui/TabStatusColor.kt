package dev.quayside.terminal.ui

import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba

/** Token colour of the dot of a tab status: warn, work, ok, err, and muted for STOPPED (docs/plans/AM-806.md). */
internal fun TabStatus.color(tokens: ColorTokens): Rgba =
    when (this) {
        TabStatus.WAITING -> tokens.status.warn
        TabStatus.WORKING -> tokens.status.work
        TabStatus.DONE -> tokens.status.ok
        TabStatus.FAILED -> tokens.status.err
        TabStatus.STOPPED -> tokens.content.muted
    }
