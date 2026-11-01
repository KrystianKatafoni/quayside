package dev.quayside.app

import dev.quayside.notifications.api.NotificationCenter
import dev.quayside.terminal.api.TerminalId
import dev.quayside.terminal.api.TerminalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Turns every terminal bell of [bells] into a notification while [settings] has `bellNotification` on (FR-T7,
 * AM-509). The setting is read when the bell rings, so a change applies to tabs that are already open. Runs in [scope]
 * until it ends.
 */
internal fun forwardBells(
    bells: Flow<TerminalId>,
    settings: StateFlow<TerminalSettings>,
    center: NotificationCenter,
    scope: CoroutineScope,
): Job =
    scope.launch {
        bells.collect { if (settings.value.bellNotification) center.notifyTerminalBell() }
    }
