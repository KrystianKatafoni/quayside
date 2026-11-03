package dev.quayside.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

private val FRAME_WIDTH = 2.dp
private val FRAME_GAP = 2.dp

/**
 * 2 dp frame in the text colour while the focusable modifier after it is focused. The frame is drawn outside the
 * element, separated from it by a 2 dp gap that shows the container background; so the frame borders on the
 * container (at least 3:1 on bg, panel, raised and sel by the AM-801 audit) and never replaces the edge of an accent
 * button. The 4 dp (frame plus gap) is always reserved, so the layout does not move when focus changes. Put it before
 * the focusable modifier.
 */
@Composable
public fun Modifier.amFocusFrame(): Modifier {
    var focused by remember { mutableStateOf(false) }
    val frame =
        if (focused) {
            Modifier.border(FRAME_WIDTH, focusColor(AmTheme.colors).toColor(), AmTheme.shape.corner)
        } else {
            Modifier
        }
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(frame)
        .padding(FRAME_WIDTH + FRAME_GAP)
}
