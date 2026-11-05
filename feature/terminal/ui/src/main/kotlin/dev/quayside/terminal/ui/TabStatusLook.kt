package dev.quayside.terminal.ui

import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.designsystem.StatusTone

/** Icon and tone of the status mark of a tab (plan AM-809, table 2 without PERMISSION). */
internal fun TabStatus.look(): Pair<IconKind, StatusTone> =
    when (this) {
        TabStatus.WAITING -> IconKind.WAITING to StatusTone.WARN
        TabStatus.WORKING -> IconKind.WORKING to StatusTone.WORK
        TabStatus.DONE -> IconKind.DONE to StatusTone.OK
        TabStatus.FAILED -> IconKind.FAILED to StatusTone.ERR
        TabStatus.STOPPED -> IconKind.STOPPED to StatusTone.NEUTRAL
    }
