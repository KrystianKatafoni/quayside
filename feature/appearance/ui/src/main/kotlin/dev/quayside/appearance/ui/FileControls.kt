package dev.quayside.appearance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmButton
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.ButtonOptions
import dev.quayside.core.designsystem.toColor

/**
 * The fixed message of a failed apply, import or export, the notice after a finished import or export, and the buttons
 * "Importuj…", "Importuj schemat…" and "Eksportuj…". The buttons are SECONDARY and never get the start focus
 * (lesson of the consent dialog); they are off while a save or a file action runs.
 */
@Composable
internal fun FileControls(
    state: AppearanceDialogState,
    onIntent: (AppearanceIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        state.error?.let {
            AmText(
                AppearanceStrings.of(it),
                Modifier.testTag(AppearanceTags.ERROR),
                color =
                    AmTheme.colors.status.err
                        .toColor(),
            )
        }
        state.notice?.let { AmText(AppearanceStrings.of(it), Modifier.testTag(AppearanceTags.NOTICE)) }
        FileButtons(state.busy, onIntent)
    }
}

/** "Importuj…", "Importuj schemat…" and "Eksportuj…"; off while [busy]. */
@Composable
private fun FileButtons(
    busy: Boolean,
    onIntent: (AppearanceIntent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        AmButton(
            AppearanceStrings["import"],
            { onIntent(AppearanceIntent.Import) },
            Modifier.testTag(AppearanceTags.IMPORT),
            ButtonOptions(enabled = !busy),
        )
        AmButton(
            AppearanceStrings["importSchemes"],
            { onIntent(AppearanceIntent.ImportSchemes) },
            Modifier.testTag(AppearanceTags.IMPORT_SCHEMES),
            ButtonOptions(enabled = !busy),
        )
        AmButton(
            AppearanceStrings["export"],
            { onIntent(AppearanceIntent.Export) },
            Modifier.testTag(AppearanceTags.EXPORT),
            ButtonOptions(enabled = !busy),
        )
    }
}
