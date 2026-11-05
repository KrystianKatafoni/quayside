package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Texts of the window shortcuts next to the actions; `app` builds them from its shortcuts (no literals here). */
public data class SessionKeyCaps(
    val attach: String,
    val reply: String,
    val stop: String,
    val newAgent: String,
)

/**
 * What `app` does on Attach, Reply and "Nowy agent"; [keyCaps] null hides the shortcut marks. Not a data class
 * (lambdas never compare equal).
 */
public class MissionControlHandlers(
    public val onAttach: () -> Unit = {},
    public val onReply: () -> Unit = {},
    public val onNewAgent: () -> Unit = {},
    public val keyCaps: SessionKeyCaps? = null,
)

/** The key caps of the handlers for the buttons below [MissionControl]; null = no marks. */
internal val LocalKeyCaps = staticCompositionLocalOf<SessionKeyCaps?> { null }
