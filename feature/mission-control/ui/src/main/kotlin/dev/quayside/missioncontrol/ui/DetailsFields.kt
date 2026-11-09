package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toColor
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The directory of the project of the page; null in a global view (the worktree is then shown in full). */
internal val LocalProjectDir = compositionLocalOf<Path?> { null }

/** The worktree as shown in the details: relative to [projectDir] when under it ("." for the directory itself). */
internal fun worktreeText(
    cwd: Path,
    projectDir: Path,
): String =
    if (cwd.startsWith(projectDir)) {
        projectDir.relativize(cwd).toString().ifEmpty { "." }
    } else {
        cwd.toString()
    }

/**
 * Worktree, ID, start, last activity and the known model, permission mode and branch as label and value pairs in two
 * columns, in a card with the theme frame.
 */
@Composable
internal fun DetailsFields(details: SessionDetails) {
    val shape = AmTheme.shape
    val worktree = LocalProjectDir.current?.let { worktreeText(details.worktree, it) } ?: details.worktree.toString()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    shape.borderWidth,
                    AmTheme.colors.surfaces.border
                        .toColor(),
                    shape.card,
                ).padding(all = 10.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Field("details.worktree", worktree)
        Field("details.id", details.id.value)
        Field("details.startedAt", timeFormat.format(details.startedAt))
        Field("details.lastActivity", timeFormat.format(details.lastActivityAt))
        details.meta.model?.let { Field("details.model", it) }
        details.meta.permissionMode?.let { Field("details.permissionMode", it) }
        details.meta.branch?.let { Field("details.branch", it) }
    }
}

@Composable
private fun Field(
    key: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        AmText(
            MissionControlStrings[key],
            modifier = Modifier.width(width = LABEL_WIDTH),
            role = TextRole.CAPTION,
            color = mutedColor(),
        )
        AmText(value, modifier = Modifier.weight(weight = 1f), role = TextRole.CODE)
    }
}

private val LABEL_WIDTH = 112.dp

/** Start and last activity in the details panel, in the zone of the Mac. */
private val timeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
