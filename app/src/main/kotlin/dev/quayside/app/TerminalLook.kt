package dev.quayside.app

import dev.quayside.terminal.api.TerminalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** [this] with the typography's [terminalFont] when the user chose no own font (`fontFamily == null`, docs/08). */
internal fun TerminalSettings.withTypographyFont(terminalFont: String): TerminalSettings =
    if (fontFamily == null) copy(fontFamily = terminalFont) else this

/** [settings] mapped by [withTypographyFont] in [scope] (Eagerly); the value is current before the first read. */
internal fun typographySettings(
    settings: StateFlow<TerminalSettings>,
    terminalFont: String,
    scope: CoroutineScope,
): StateFlow<TerminalSettings> =
    settings
        .map { it.withTypographyFont(terminalFont) }
        .stateIn(scope, SharingStarted.Eagerly, settings.value.withTypographyFont(terminalFont))
