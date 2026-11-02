package dev.quayside.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.quayside.agentruntime.api.HookError
import dev.quayside.agentruntime.api.HookKind
import dev.quayside.agentruntime.api.HookStatus
import java.nio.file.Path

/** Semantics tags of the hooks dialog (AM-607). */
internal object HookTags {
    const val DIALOG = "hooks.dialog"
    const val INFO = "hooks.info"
    const val STATUS = "hooks.status"
    const val NOTICE = "hooks.notice"
    const val INSTALL = "hooks.install"
    const val UNINSTALL = "hooks.uninstall"
    const val CLOSE = "hooks.close"
}

/** Colors of the dialog; every text color has contrast of at least 4.5:1 on [panel] (NFR-6). */
internal object HookColors {
    val panel = Color(color = 0xFF2B2B2B)
    val primary = Color(color = 0xFFF0F0F0)
    val secondary = Color(color = 0xFFB8B8B8)
    val error = Color(color = 0xFFFF8A80)

    /** Border of the focused button; contrast of at least 3:1 on [panel] (WCAG 1.4.11). */
    val focusBorder = Color(color = 0xFF8AB4F8)
    val button = Color(color = 0xFF3C3C3C)
}

private val panelShape = RoundedCornerShape(size = 10.dp)
private val buttonShape = RoundedCornerShape(size = 4.dp)

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
            modifier = Modifier.fillMaxSize().background(color = Color(color = 0x99000000)),
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
    Column(
        modifier =
            Modifier
                .width(width = 560.dp)
                .background(color = HookColors.panel, shape = panelShape)
                .padding(all = 16.dp)
                .testTag(HookTags.DIALOG)
                .onPreviewKeyEvent { event ->
                    val escape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                    if (escape) controller.close()
                    escape
                },
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        HookText(Strings["hooks.title"], size = 15)
        HookText(infoText(backupDir), HookColors.secondary, HookTags.INFO)
        HookText(statusText(state.status), tag = HookTags.STATUS)
        state.notice?.let { HookText(noticeText(it, backupDir), noticeColor(it), HookTags.NOTICE) }
        HookButtons(state, controller)
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

/** A button with a visible focus frame in [HookColors.focusBorder]. */
@Composable
private fun HookButton(
    tag: String,
    textKey: String,
    enabled: Boolean,
    requester: FocusRequester = remember { FocusRequester() },
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(value = false) }
    val frame = if (focused) HookColors.focusBorder else Color.Transparent
    Box(
        Modifier
            .testTag(tag)
            .background(color = HookColors.button, shape = buttonShape)
            .border(width = 2.dp, color = frame, shape = buttonShape)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        HookText(Strings[textKey], if (enabled) HookColors.primary else HookColors.secondary)
    }
}

@Composable
private fun HookText(
    text: String,
    color: Color = HookColors.primary,
    tag: String? = null,
    size: Int = 13,
) {
    BasicText(
        text = text,
        modifier = if (tag == null) Modifier else Modifier.testTag(tag),
        style = TextStyle(color = color, fontSize = size.sp),
    )
}

/** What changes before the install: the port and the backup folder come from the app wiring, not from constants. */
private fun infoText(backupDir: Path): String = Strings.format("hooks.info", HOOK_PORT.toString(), backupDir.toString())

private fun statusText(status: HookStatus?): String =
    when (status) {
        null -> {
            Strings["hooks.status.reading"]
        }

        HookStatus.NotInstalled -> {
            Strings["hooks.status.notInstalled"]
        }

        HookStatus.Installed -> {
            Strings["hooks.status.installed"]
        }

        is HookStatus.Partial -> {
            Strings.format("hooks.status.partial", status.missing.sorted().joinToString { it.eventName() })
        }

        is HookStatus.Unreadable -> {
            Strings["hooks.status.unreadable"]
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

private fun noticeColor(notice: HookNotice): Color =
    if (notice is HookNotice.Failed) HookColors.error else HookColors.primary

/** The name of the event in the Claude Code settings file. */
private fun HookKind.eventName(): String =
    when (this) {
        HookKind.NOTIFICATION -> "Notification"
        HookKind.STOP -> "Stop"
        HookKind.STOP_FAILURE -> "StopFailure"
        HookKind.SESSION_START -> "SessionStart"
        HookKind.SESSION_END -> "SessionEnd"
    }

/** Places the dialog layer over the whole window, whatever the anchor. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
