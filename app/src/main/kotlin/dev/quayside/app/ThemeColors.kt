package dev.quayside.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.toColor

// Colours of the current theme under short names; the screens read tokens only through AmTheme (AM-806).

@Composable
internal fun mutedColor(): Color =
    AmTheme.colors.content.muted
        .toColor()

@Composable
internal fun errColor(): Color =
    AmTheme.colors.status.err
        .toColor()
