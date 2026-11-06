package dev.quayside.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.quayside.missioncontrol.ui.MissionControl
import dev.quayside.missioncontrol.ui.StatusBar

/**
 * The preview of the Appearance dialog (ADR 0022): the real Mission Control over the sample data, with the status bar
 * under it (Mission Control no longer draws it, AM-816). Its controller lives in the scope of the slot, so it stops
 * ticking when the dialog closes.
 */
@Composable
internal fun AppearancePreviewSlot() {
    val scope = rememberCoroutineScope()
    val controller = remember { previewController(scope) }
    Column {
        MissionControl(controller, Modifier.weight(weight = 1f))
        StatusBar(controller, terminal = false, supervisor = null)
    }
}
