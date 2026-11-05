package dev.quayside.terminal.ui

import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Holds a terminal widget in a SwingPanel. While [blocked] says a modal Compose layer is shown, no point of it is
 * "inside" for AWT hit testing, so mouse clicks reach the layer above and not the widget, which AWT would pick
 * first (the interop container is above the layer in the z order). The widget stays visible under the scrim.
 */
internal class ModalShield(
    component: JComponent,
    private val blocked: () -> Boolean,
) : JPanel(BorderLayout()) {
    init {
        add(component, BorderLayout.CENTER)
    }

    override fun contains(
        x: Int,
        y: Int,
    ): Boolean = !blocked() && super.contains(x, y)
}
