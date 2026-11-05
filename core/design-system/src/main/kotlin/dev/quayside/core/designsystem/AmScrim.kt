package dev.quayside.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color

private val SCRIM = Color(0x99000000)

/**
 * Dims everything behind a modal layer: black at 60 % alpha in every theme (the scrim of today's dialogs). While
 * it is composed the layer counts in [LocalAmModalGate] and lets embedded Swing views yield to it
 * (no clicks, no focus returned to them).
 * The counter is a side effect: this modifier is more than a background, use it on modal layers only.
 */
public fun Modifier.amScrim(): Modifier = amModalLayer().background(SCRIM)

/**
 * Counts the element as a modal layer in [LocalAmModalGate] while it is composed, without drawing anything
 * ([amScrim] is this plus the dimming). The open list of an [AmDropdown] is such a layer.
 */
internal fun Modifier.amModalLayer(): Modifier =
    composed {
        val gate = LocalAmModalGate.current
        DisposableEffect(gate) {
            gate.enter()
            onDispose { gate.leave() }
        }
        Modifier
    }
