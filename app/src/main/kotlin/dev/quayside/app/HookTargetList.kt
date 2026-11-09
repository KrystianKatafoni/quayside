package dev.quayside.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.agentruntime.api.HookKind
import dev.quayside.agentruntime.api.HookStatus
import dev.quayside.core.designsystem.AmListRow
import dev.quayside.core.designsystem.AmText

/** The scopes of the hooks dialog, each with its status. A click selects a row and never writes a file. */
@Composable
internal fun HookTargetList(
    state: HookDialogState,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
                .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        state.rows.forEachIndexed { index, row ->
            AmListRow(
                selected = index == state.selected,
                onClick = { onSelect(index) },
                modifier = Modifier.testTag(HookTags.row(index)),
            ) {
                AmText(rowName(row.target), maxLines = 1)
                AmText(rowStatus(row))
            }
        }
    }
}

private fun rowName(target: HookTarget): String =
    when (target) {
        HookTarget.Global -> Strings["hooks.row.global"]
        is HookTarget.Project -> target.name
    }

/** The status text of [row], or the missing-folder text. */
internal fun rowStatus(row: HookRow): String =
    if (row.target.isPresent()) statusText(row.status) else Strings["hooks.status.missingDir"]

/** The status line text; [status] null means it is still read. */
internal fun statusText(status: HookStatus?): String =
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

/** The name of the event in the Claude Code settings file. */
internal fun HookKind.eventName(): String =
    when (this) {
        HookKind.NOTIFICATION -> "Notification"
        HookKind.STOP -> "Stop"
        HookKind.STOP_FAILURE -> "StopFailure"
        HookKind.SESSION_START -> "SessionStart"
        HookKind.SESSION_END -> "SessionEnd"
    }
