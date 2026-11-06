package dev.quayside.terminal.impl

import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JScrollBar
import javax.swing.plaf.basic.BasicScrollBarUI

private const val BAR_WIDTH = 10
private const val BAR_HEIGHT = 48
private const val MIN_THUMB_HEIGHT = 24
private const val THUMB_INSET = 2
private const val THUMB_ARC = 4

/** Vertical scroll bar that always paints with [colors], whatever the look and feel; [updateUI] keeps that UI. */
internal class ThemedScrollBar(
    colors: ScrollbarColors,
) : JScrollBar(VERTICAL) {
    // Null while the super constructor calls updateUI(): fields of this class are not set yet.
    private val themed: ScrollbarColors? = colors

    init {
        updateUI()
    }

    override fun updateUI() {
        val colors = themed
        if (colors == null) super.updateUI() else setUI(ThemedScrollBarUI(colors))
    }
}

/**
 * Paints track and a rounded thumb (inset 2 px) in [colors]; no arrow buttons; width 10 px; thumb at least 24 px
 * high; when the model shows everything (`extent >= max - min`) the thumb covers the whole track.
 */
internal class ThemedScrollBarUI(
    private val colors: ScrollbarColors,
) : BasicScrollBarUI() {
    override fun installDefaults() {
        super.installDefaults()
        scrollbar.background = colors.background.toAwtColor()
    }

    override fun getPreferredSize(c: JComponent): Dimension = Dimension(BAR_WIDTH, BAR_HEIGHT)

    override fun getMinimumThumbSize(): Dimension = Dimension(BAR_WIDTH, MIN_THUMB_HEIGHT)

    override fun createDecreaseButton(orientation: Int): JButton = NoButton()

    override fun createIncreaseButton(orientation: Int): JButton = NoButton()

    override fun getThumbBounds(): Rectangle =
        if (scrollbar.model.extent >= scrollbar.model.maximum - scrollbar.model.minimum) {
            Rectangle(trackRect)
        } else {
            super.getThumbBounds()
        }

    override fun paintTrack(
        g: Graphics,
        c: JComponent,
        trackBounds: Rectangle,
    ) {
        // Safeguard for a track smaller than the component: the background shows around it.
        g.color = colors.background.toAwtColor()
        g.fillRect(0, 0, c.width, c.height)
        g.color = colors.track.toAwtColor()
        g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height)
    }

    override fun paintThumb(
        g: Graphics,
        c: JComponent,
        thumbBounds: Rectangle,
    ) {
        val canvas = g.create() as Graphics2D
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            canvas.color = colors.thumb.toAwtColor()
            canvas.fillRoundRect(
                thumbBounds.x + THUMB_INSET,
                thumbBounds.y + THUMB_INSET,
                thumbBounds.width - 2 * THUMB_INSET,
                thumbBounds.height - 2 * THUMB_INSET,
                THUMB_ARC,
                THUMB_ARC,
            )
        } finally {
            canvas.dispose()
        }
    }

    /** Zero-sized stand-in for an arrow button. */
    private class NoButton : JButton() {
        init {
            isFocusable = false
        }

        override fun getPreferredSize(): Dimension = Dimension(0, 0)

        override fun getMinimumSize(): Dimension = Dimension(0, 0)

        override fun getMaximumSize(): Dimension = Dimension(0, 0)
    }
}
