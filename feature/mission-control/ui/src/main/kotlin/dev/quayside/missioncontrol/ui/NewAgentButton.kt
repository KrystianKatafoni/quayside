package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.ButtonKind
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.IconKind

/**
 * The primary "Nowy agent" button at the right edge of a page title row; draws nothing without a `newAgent` handler.
 * Never focusable, so Space and Enter stay window shortcuts.
 */
@Composable
internal fun NewAgentButton() {
    val action = LocalNewAgent.current ?: return
    AmButton(
        MissionControlStrings["newAgent.button"],
        action.onClick,
        Modifier.testTag(MissionControlTags.NEW_AGENT_BUTTON),
        ButtonOptions(
            kind = ButtonKind.PRIMARY,
            focusable = false,
            enabled = action.enabled,
            icon = IconKind.PLUS,
            shortcut = action.keyCap,
        ),
    )
}
