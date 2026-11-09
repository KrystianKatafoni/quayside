package dev.quayside.missioncontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.quayside.core.designsystem.AmText
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toColor

/** A counter at zero: a ring in the status token of [tone] over the background of its row, read as [description]. */
@Composable
internal fun ZeroCounter(
    tone: StatusTone,
    description: String,
    selected: Boolean,
) {
    val paint = zeroCounterColors(tone, selected, AmTheme.colors)
    val shape = AmTheme.shape
    Box(
        modifier =
            Modifier
                .clearAndSetSemantics { contentDescription = description }
                .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                .background(paint.fill.toColor(), shape.counter)
                .border(1.dp, paint.ring.toColor(), shape.counter)
                .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AmText("0", role = TextRole.CAPTION, color = paint.label.toColor())
    }
}
