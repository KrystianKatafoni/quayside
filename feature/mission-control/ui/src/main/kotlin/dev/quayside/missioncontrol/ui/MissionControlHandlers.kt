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
 * The "Nowy agent" button at the right of the page title: what `app` does on a click ([onClick]), whether it is
 * [enabled] (as the menu item) and the text of its shortcut ([keyCap], null = none). Not a data class (lambda).
 */
public class NewAgentAction(
    public val onClick: () -> Unit,
    public val enabled: Boolean = true,
    public val keyCap: String? = null,
)

/**
 * What `app` does on Attach, Reply and a click on a subfolder row; [keyCaps] null hides the shortcut marks;
 * [newAgent] null = no "Nowy agent" button. Not a data class (lambdas never compare equal).
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
    public val newAgent: NewAgentAction? = null,
)

/** The "Nowy agent" button of the handlers for the page headers; null = none. */
internal val LocalNewAgent = staticCompositionLocalOf<NewAgentAction?> { null }

/** The key caps of the handlers for the buttons below [MissionControl]; null = no marks. */
internal val LocalKeyCaps = staticCompositionLocalOf<SessionKeyCaps?> { null }
