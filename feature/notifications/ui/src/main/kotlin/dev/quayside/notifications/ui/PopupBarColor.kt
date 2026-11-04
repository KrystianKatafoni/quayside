package dev.quayside.notifications.ui

import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.notifications.api.NotificationKind

/**
 * Token colour of the bar of a popup card, by kind of the notification (docs/plans/AM-806.md, table 1). The title of
 * the card says the kind too, so the bar is never the only signal (NFR-6).
 */
internal fun NotificationKind.barColor(tokens: ColorTokens): Rgba =
    when (this) {
        NotificationKind.NEEDS_INPUT, NotificationKind.PERMISSION -> tokens.status.warn
        NotificationKind.FAILED -> tokens.status.err
        NotificationKind.TURN_ENDED -> tokens.status.ok
        NotificationKind.PULL_REQUEST -> tokens.content.accent
        NotificationKind.RUNNING_LONG -> tokens.status.work
        NotificationKind.TERMINAL_BELL -> tokens.content.muted
    }
