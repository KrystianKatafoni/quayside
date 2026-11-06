package dev.quayside.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.quayside.agentruntime.api.HookError
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmPanel
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.amScrim
import java.nio.file.Path

/**
 * The "Hooki Claude Code" dialog over the whole window (AM-607, NFR-5); draws nothing while [controller] has it
 * closed. A focusable popup over the whole window, so keys reach its buttons and not the window behind it; Escape
 * is the close button. [backupDir] is shown after an install.
 */
@Composable
internal fun HookSetupDialog(
    controller: HookSetupController,
    backupDir: Path,
) {
    val state by controller.state.collectAsState()
    val shown = state ?: return
    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = controller::close,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().amScrim(),
            contentAlignment = Alignment.Center,
        ) {
            HookPanel(shown, controller, backupDir)
        }
    }
}

@Composable
private fun HookPanel(
    state: HookDialogState,
    controller: HookSetupController,
    backupDir: Path,
) {
    AmPanel(
        modifier =
            Modifier
                .width(width = 560.dp)
                .testTag(HookTags.DIALOG)
                .onPreviewKeyEvent { event ->
                    val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                    if (escape) controller.close()
                    escape
                },
    ) {
        Column(
            modifier = Modifier.padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 10.dp),
        ) {
            AmText(Strings["hooks.title"], role = TextRole.HEADING)
            HookText(infoText(state.current.target, backupDir), HookTags.INFO, mutedColor())
            HookText(rowStatus(state.current), HookTags.STATUS)
            state.notice?.let { HookText(noticeText(it, backupDir), HookTags.NOTICE, noticeColor(it)) }
            HookButtons(state, controller)
            HookTargetList(state, controller::select)
        }
    }
}

/**
 * Close/Skip, Install and Uninstall in the order of Tab. The focus starts on Close/Skip and stays there: Enter or
 * Space pressed at once after the dialog shows must never change the user's settings file (NFR-5).
 */
@Composable
private fun HookButtons(
    state: HookDialogState,
    controller: HookSetupController,
) {
    val closeFocus = remember { FocusRequester() }
    // Again when an action starts or ends: its button turns off and must not hand the focus to the other action.
    LaunchedEffect(state.busy, state.notice) { closeFocus.requestFocus() }
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        val closeKey = if (state.firstRun) "hooks.skip" else "hooks.close"
        HookButton(HookTags.CLOSE, closeKey, enabled = true, closeFocus, controller::close)
        HookButton(HookTags.INSTALL, "hooks.install", state.canInstall, onClick = controller::install)
        HookButton(HookTags.UNINSTALL, "hooks.uninstall", state.canUninstall, onClick = controller::uninstall)
    }
}

@Composable
private fun HookButton(
    tag: String,
    textKey: String,
    enabled: Boolean,
    requester: FocusRequester = remember { FocusRequester() },
    onClick: () -> Unit,
) {
    AmButton(
        label = Strings[textKey],
        onClick = onClick,
        modifier = Modifier.testTag(tag).focusRequester(requester),
        options = ButtonOptions(enabled = enabled),
    )
}

/** Wrapping text of the dialog in the colour [color] (the container colour when null). */
@Composable
private fun HookText(
    text: String,
    tag: String,
    color: Color? = null,
) {
    AmText(text, modifier = Modifier.testTag(tag), color = color, maxLines = Int.MAX_VALUE)
}

/** What changes before the install: the port and the backup folder come from the app wiring, not from constants. */
private fun infoText(
    target: HookTarget,
    backupDir: Path,
): String =
    when (target) {
        HookTarget.Global -> {
            Strings.format("hooks.info", HOOK_PORT.toString(), backupDir.toString())
        }

        is HookTarget.Project -> {
            val file =
                target.dir
                    .resolve(".claude")
                    .resolve("settings.local.json")
                    .toString()
            Strings.format("hooks.info.project", file, HOOK_PORT.toString(), backupDir.toString())
        }
    }

private fun noticeText(
    notice: HookNotice,
    backupDir: Path,
): String =
    when (notice) {
        HookNotice.Installed -> Strings.format("hooks.notice.installed", backupDir.toString())
        HookNotice.Uninstalled -> Strings["hooks.notice.uninstalled"]
        is HookNotice.Failed -> errorText(notice.error)
    }

/** A fixed text per error type with the file path; the `reason` of the error is never shown (ENG-C8). */
private fun errorText(error: HookError): String =
    when (error) {
        is HookError.SettingsUnreadable -> Strings.format("hooks.error.unreadable", error.file.toString())
        is HookError.WriteFailed -> Strings.format("hooks.error.writeFailed", error.file.toString())
        is HookError.BackupNotFound -> Strings["hooks.error.backupNotFound"]
    }

@Composable
private fun noticeColor(notice: HookNotice): Color? = if (notice is HookNotice.Failed) errColor() else null

/** Places the dialog layer over the whole window, whatever the anchor. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
