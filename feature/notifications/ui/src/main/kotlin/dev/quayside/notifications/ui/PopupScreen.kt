package dev.quayside.notifications.ui

import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/** The usable area of the main screen: its bounds minus the menu bar and the Dock. */
internal fun currentScreenArea(): ScreenArea {
    val configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    val bounds = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    return ScreenArea(
        x = bounds.x + insets.left,
        y = bounds.y + insets.top,
        width = bounds.width - insets.left - insets.right,
        height = bounds.height - insets.top - insets.bottom,
    )
}
