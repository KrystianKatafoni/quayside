package dev.quayside.core.designsystem

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import java.awt.Component
import java.awt.KeyboardFocusManager

/**
 * Counts the modal layers (dialogs, the palette) shown over a window; [amScrim] opens one while it is composed.
 * Heavyweight Swing views embedded in Compose (the terminals, ADR 0012) read [active]: under a modal layer they
 * must neither take mouse clicks nor keep the keyboard focus. Backed by snapshot state, so composables can read it.
 */
public class AmModalGate {
    private val depth = mutableIntStateOf(value = 0)
    private var focusBefore: Component? = null

    /** True while at least one modal layer is shown. */
    public val active: Boolean get() = depth.intValue > 0

    /** The AWT focus owner at the moment the first layer was shown (null: none, or already taken). */
    public fun takeFocusBeforeModal(): Component? = focusBefore.also { focusBefore = null }

    internal fun enter() {
        if (depth.intValue == 0) focusBefore = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        depth.intValue += 1
    }

    internal fun leave() {
        depth.intValue = (depth.intValue - 1).coerceAtLeast(minimumValue = 0)
    }
}

private val DefaultModalGate = AmModalGate()

/**
 * The gate of the window. The app provides one per window; the default instance serves previews and tests.
 */
public val LocalAmModalGate: ProvidableCompositionLocal<AmModalGate> = compositionLocalOf { DefaultModalGate }
