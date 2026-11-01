package dev.quayside.terminal.impl

import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.TerminalPanel
import com.jediterm.terminal.ui.settings.SettingsProvider
import dev.quayside.terminal.api.SearchMatch
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * What the terminal panel of a searchable widget draws (plan AM-504, decision 7): [hits] are all hits of the last
 * search, [selected] the current one. Rows are absolute (0 = oldest scrollback line). Swing thread only.
 */
internal interface SearchHighlights {
    val hits: List<SearchMatch>
    val selected: SearchMatch?
}

/** A [JediTermWidget] whose terminal panel is a [HighlightingPanel]; the panel is created by the super constructor. */
internal class SearchableWidget(
    settings: SettingsProvider,
) : JediTermWidget(settings) {
    /** The panel that draws search hits, null only if the base class stopped using [createTerminalPanel]. */
    val highlighting: HighlightingPanel? get() = terminalPanel as? HighlightingPanel

    override fun createTerminalPanel(
        settingsProvider: SettingsProvider,
        styleState: StyleState,
        backBuffer: TerminalTextBuffer,
    ): TerminalPanel = HighlightingPanel(settingsProvider, backBuffer, styleState)
}

/**
 * The box of [hit] in panel pixels: `x = insetX + start * cell.width`, `y = (row - history - origin) * cell.height`,
 * width `(end - start) * cell.width`, height `cell.height`. [history] is the scrollback line count and [origin] the
 * vertical scroll origin (negative when scrolled into the history).
 */
internal fun hitBounds(
    hit: SearchMatch,
    history: Int,
    origin: Int,
    insetX: Int,
    cell: Dimension,
): Rectangle =
    Rectangle(
        insetX + hit.start * cell.width,
        (hit.row - history - origin) * cell.height,
        (hit.end - hit.start) * cell.width,
        cell.height,
    )

/**
 * Draws [SearchHighlights] over the text: after `super.paintComponent`, a translucent box per hit and a stronger
 * box with an outline for the selected one. Uses JediTerm's protected `myCharSize`, `myClientScrollOrigin` and
 * `getInsetX()`, so a JediTerm upgrade can break it (covered by `JediTermSearchTest`).
 */
internal class HighlightingPanel(
    settings: SettingsProvider,
    private val buffer: TerminalTextBuffer,
    styleState: StyleState,
) : TerminalPanel(settings, buffer, styleState),
    SearchHighlights {
    private var shownHits: List<SearchMatch> = emptyList()
    private var shownSelected: SearchMatch? = null

    /**
     * Called for every bell of the terminal (AM-509), on the emulator thread; set once after the widget is built,
     * because the panel is created inside the super constructor.
     */
    @Volatile
    var onBell: (() -> Unit)? = null

    override fun beep() {
        super.beep()
        onBell?.invoke()
    }

    override val hits: List<SearchMatch> get() = shownHits
    override val selected: SearchMatch? get() = shownSelected

    /** Swing thread. Replaces what is drawn; [selected] is one of [hits] or null. */
    fun show(
        hits: List<SearchMatch>,
        selected: SearchMatch?,
    ) {
        shownHits = hits
        shownSelected = selected
        repaint()
    }

    /** Swing thread. Scrolls vertically so that absolute [row] is shown, centred when it was out of view. */
    fun reveal(row: Int) {
        val relative = row - buffer.historyLinesCount
        val origin = verticalScrollModel.value
        if (relative < origin || relative >= origin + buffer.height) {
            verticalScrollModel.value = relative - buffer.height / 2
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (shownHits.isEmpty()) return
        val canvas = g.create()
        try {
            if (canvas is Graphics2D) {
                val history = buffer.historyLinesCount
                shownHits.forEach { paintHit(canvas, it, history) }
            }
        } finally {
            canvas.dispose()
        }
    }

    private fun paintHit(
        g: Graphics2D,
        hit: SearchMatch,
        history: Int,
    ) {
        val box = hitBounds(hit, history, myClientScrollOrigin, insetX, myCharSize)
        if (box.y + box.height <= 0 || box.y >= height) return
        val current = hit == shownSelected
        g.color = if (current) CURRENT_FILL else HIT_FILL
        g.fillRect(box.x, box.y, box.width, box.height)
        if (current) {
            g.color = CURRENT_OUTLINE
            g.drawRect(box.x, box.y, box.width - 1, box.height - 1)
        }
    }

    private companion object {
        // Fixed accent until the colour scheme exists (AM-8xx).
        val HIT_FILL = Color(255, 200, 0, 90)
        val CURRENT_FILL = Color(255, 140, 0, 170)
        val CURRENT_OUTLINE = Color(255, 90, 0)
    }
}
