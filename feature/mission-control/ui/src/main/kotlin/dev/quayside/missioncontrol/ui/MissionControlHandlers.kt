package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.staticCompositionLocalOf
import dev.quayside.projects.api.ProjectFocus

/** Texts of the window shortcuts next to the actions; `app` builds them from its shortcuts (no literals here). */
public data class SessionKeyCaps(
    val attach: String,
    val reply: String,
    val stop: String,
    val next: String,
    val previous: String,
    val preview: String,
)

/**
 * What `app` does on Attach, Reply and a click on a subfolder row; [keyCaps] null hides the shortcut marks. Not a
 * data class (lambdas never compare equal).
 */
public class MissionControlHandlers(
    public val onAttach: () -> Unit = {},
    public val onReply: () -> Unit = {},
    public val keyCaps: SessionKeyCaps? = null,
    /**
     * A click on a subfolder row of the group overview (FR-P8); `app` selects that focus in the sidebar, Mission
     * Control follows through `selected`. Never called for the "folder główny" row.
     */
    public val onOpenSubfolder: (ProjectFocus) -> Unit = {},
)

/** The key caps of the handlers for the buttons below [MissionControl]; null = no marks. */
internal val LocalKeyCaps = staticCompositionLocalOf<SessionKeyCaps?> { null }
