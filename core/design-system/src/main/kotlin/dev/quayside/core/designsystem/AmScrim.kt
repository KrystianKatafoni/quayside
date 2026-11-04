package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val SCRIM = Color(0x99000000)

/** Dims everything behind a modal layer: black at 60 % alpha in every theme (the scrim of today's dialogs). */
public fun Modifier.amScrim(): Modifier = background(SCRIM)
