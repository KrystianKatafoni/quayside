package dev.quayside.app

import dev.quayside.core.designtokens.TerminalColors
import dev.quayside.terminal.api.TerminalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** [this] with the typography's [terminalFont] when the user chose no own font (`fontFamily == null`, docs/08). */
internal fun TerminalSettings.withTypographyFont(terminalFont: String): TerminalSettings =
    if (fontFamily == null) copy(fontFamily = terminalFont) else this

/**
 * [settings] with the terminal font of the look that is applied now ([withTypographyFont]), in [scope] (Eagerly); it
 * follows both [settings] and [looks]. Tabs opened later get the new font (ADR 0016).
 */
internal fun appliedTerminalSettings(
    settings: StateFlow<TerminalSettings>,
    looks: StateFlow<AppLook>,
    scope: CoroutineScope,
): StateFlow<TerminalSettings> {
    val applied = { stored: TerminalSettings, look: AppLook -> stored.withTypographyFont(look.typography.terminalFont) }
    return combine(settings, looks, applied)
        .stateIn(scope, SharingStarted.Eagerly, applied(settings.value, looks.value))
}

/** The terminal colours of the look that is applied now, in [scope] (Eagerly); tabs opened later get them. */
internal fun appliedTerminalColors(
    looks: StateFlow<AppLook>,
    scope: CoroutineScope,
): StateFlow<TerminalColors> =
    looks
        .map { it.colors.terminal }
        .stateIn(scope, SharingStarted.Eagerly, looks.value.colors.terminal)
