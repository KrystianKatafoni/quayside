package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

/**
 * Surface with the corner radius, border width and border colour of the theme; [raised] = raised instead of panel.
 * Resets the default text colour of [AmText] to content.text, so a panel inside [AmModeBar] does not inherit muted.
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
                    .clip(shape.corner)
                    .background(fill.toColor())
                    .border(shape.borderWidth, border.toColor(), shape.corner),
            content = content,
        )
    }
}
