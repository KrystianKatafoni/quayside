package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/**
 * Pill on the tone background: optional [icon] and [label] (CAPTION) in tone colours with a 4.5:1 fallback to
 * content.text. Not focusable. [description] replaces the label for screen readers; required when the label alone
 * does not say what it counts ("2" -> "2 sessions waiting").
 */
@Composable
public fun AmChip(
    label: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.NEUTRAL,
    icon: IconKind? = null,
    description: String? = null,
) {
    val colors = chipColors(tone, AmTheme.colors)
    val announced =
        if (description == null) Modifier else Modifier.clearAndSetSemantics { contentDescription = description }
    Row(
        modifier =
            modifier
                .then(announced)
                .background(colors.background.toColor(), AmTheme.shape.corner)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) AmIcon(icon, null, Modifier.size(12.dp), colors.mark.toColor())
        AmText(label, role = TextRole.CAPTION, color = colors.label.toColor())
    }
}
