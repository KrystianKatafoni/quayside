package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BADGE_SIZE = 18.dp
private val BADGE_OFFSET = 6.dp

private fun IconButtonSize.target(): Dp =
    when (this) {
        IconButtonSize.COMPACT -> 28.dp
        IconButtonSize.REGULAR -> 34.dp
        IconButtonSize.LARGE -> 44.dp
    }

private fun IconButtonSize.glyph(): Dp =
    when (this) {
        IconButtonSize.COMPACT -> 16.dp
        IconButtonSize.REGULAR -> 18.dp
        IconButtonSize.LARGE -> 20.dp
    }

/**
 * Icon-only button (Role.Button) announced by [label] (NFR-6). It draws no hover or press overlay: the style says
 * whether it is selected, and an overlay would tint the token colours. Focusable variant: amFocusFrame outside the
 * target, Space and Enter click it. The badge is drawn over the top-right corner, so a parent must not clip it.
 *
 * @throws IllegalArgumentException when [label] is blank (programmer error).
 */
@Composable
public fun AmIconButton(
    icon: IconKind,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    options: IconButtonOptions = IconButtonOptions(),
) {
    require(label.isNotBlank()) { "label must not be blank" }
    val colors = iconButtonColors(options, AmTheme.colors)
    val shape = AmTheme.shape
    val frame = if (options.focusable) Modifier.amFocusFrame() else Modifier.focusProperties { canFocus = false }
    val outline =
        colors.border?.let { Modifier.border(shape.borderWidth, it.toColor(), shape.corner) } ?: Modifier
    Box(
        modifier =
            modifier
                .then(frame)
                .size(options.size.target())
                .background(colors.background.toColor(), shape.corner)
                .then(outline)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = options.enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        AmIcon(icon, null, Modifier.size(options.size.glyph()), colors.icon.toColor())
        options.badge?.let { CounterBadge(it, Modifier.align(Alignment.TopEnd)) }
    }
}

/**
 * Counter circle. The digits are drawn with a text layout, not placed as a text node, so that the badge has no
 * semantics node even in the unmerged tree (clearAndSetSemantics does not hide it there); the count belongs in the
 * button label. The layout is keyed by the measurer and the density, so a window moved to another screen is measured
 * again.
 */
@Composable
private fun CounterBadge(
    text: String,
    modifier: Modifier,
) {
    val (background, ink) = counterBadgeColors(AmTheme.colors)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style =
        AmTheme.typography
            .style(TextRole.CAPTION)
            .copy(color = ink.toColor(), fontWeight = FontWeight.SemiBold)
    val layout = remember(text, style, measurer, density) { measurer.measure(text, style, maxLines = 1) }
    Box(
        modifier =
            modifier
                .offset(x = BADGE_OFFSET, y = -BADGE_OFFSET)
                .defaultMinSize(BADGE_SIZE, BADGE_SIZE)
                .background(background.toColor(), CircleShape)
                .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            Modifier
                .size(with(density) { layout.size.width.toDp() }, with(density) { layout.size.height.toDp() })
                .drawBehind { drawText(layout) },
        )
    }
}
