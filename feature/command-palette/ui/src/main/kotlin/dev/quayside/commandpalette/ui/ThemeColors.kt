package dev.quayside.commandpalette.ui

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
internal fun textColor(): Color =
    AmTheme.colors.content.text
        .toColor()

@Composable
internal fun raisedColor(): Color =
    AmTheme.colors.surfaces.raised
        .toColor()

@Composable
internal fun selColor(): Color =
    AmTheme.colors.surfaces.sel
        .toColor()
