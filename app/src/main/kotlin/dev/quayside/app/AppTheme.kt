package dev.quayside.app

import androidx.compose.runtime.Composable
import dev.quayside.core.designsystem.AmTheme

/** AmTheme over [content] with [look]; wraps both windows inside `application {}` in Main. */
@Composable
internal fun AppTheme(
    look: AppLook,
    content: @Composable () -> Unit,
) {
    AmTheme(look.colors, look.shape, look.typography, content)
}
