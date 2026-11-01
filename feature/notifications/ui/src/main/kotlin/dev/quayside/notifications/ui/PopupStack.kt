package dev.quayside.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.quayside.notifications.api.PopupAction
import dev.quayside.notifications.api.PopupId

/** The cards of [state], newest first. Separate from the window so that tests can compose it directly. */
@Composable
internal fun PopupStack(
    state: PopupUiState,
    onAction: (PopupId, PopupAction) -> Unit,
) {
    Column(
        modifier = Modifier.padding(PopupMetrics.PADDING.dp).testTag(PopupTags.STACK),
        verticalArrangement = Arrangement.spacedBy(PopupMetrics.GAP.dp),
    ) {
        state.popups.forEach { popup -> PopupCard(popup, state.style, onAction) }
    }
}
