package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.quayside.core.designtokens.ShadowSpec

/**
 * Surface with the card radius (r+4), border width and border colour of the theme; [raised] = raised instead of panel.
 * Draws the theme shadow (when there is one) with Compose `dropShadow`, i.e. behind the rest of the content; unlike
 * CSS box-shadow it does not cut out the interior, so it shows through a translucent panel. Resets the default text colour of [AmText]
 * to content.text, so a panel inside [AmModeBar] does not inherit muted.
 */
@Composable
public fun AmPanel(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = AmTheme.shape
    val (fill, border) = panelColors(raised, AmTheme.colors)
    CompositionLocalProvider(LocalTextColor provides null) {
        Box(
            modifier =
                modifier
                    .shadowOf(shape)
                    .clip(shape.card)
                    .background(fill.toColor())
                    .border(shape.borderWidth, border.toColor(), shape.card),
            content = content,
        )
    }
}

private fun Modifier.shadowOf(shape: AmShape): Modifier =
    shape.shadow?.let { dropShadow(shape.card, it.toShadow()) } ?: this

/** Maps the shadow token to a Compose [Shadow]: blur is the radius, the offset is vertical only. */
internal fun ShadowSpec.toShadow(): Shadow =
    Shadow(
        radius = blur.dp,
        color = color.toColor(),
        spread = spread.dp,
        offset = DpOffset(0.dp, offsetY.dp),
    )
