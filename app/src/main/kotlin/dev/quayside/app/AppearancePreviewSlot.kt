package dev.quayside.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.quayside.missioncontrol.ui.MissionControl

/**
 * The preview of the Appearance dialog (ADR 0022): the real Mission Control over the sample data. Its controller
 * lives in the scope of the slot, so it stops ticking when the dialog closes.
 */
@Composable
internal fun AppearancePreviewSlot() {
    val scope = rememberCoroutineScope()
    val controller = remember { previewController(scope) }
    MissionControl(controller)
}
