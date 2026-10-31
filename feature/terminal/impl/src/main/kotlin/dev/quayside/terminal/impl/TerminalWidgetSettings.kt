package dev.quayside.terminal.impl

import com.jediterm.terminal.ui.TerminalActionPresentation
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import dev.quayside.terminal.api.TerminalSettings
import java.awt.Font
import java.awt.font.TextAttribute
import javax.swing.KeyStroke

/**
 * JediTerm widget settings: a snapshot of [settings] taken when the widget is built (ADR 0016), no copy on
 * selection, Cmd-K/F left free. [fontAvailable] says whether a font family is installed; a missing one falls back to
 * the JediTerm default font. It is asked at most once, when the widget is built.
 */
internal class TerminalWidgetSettings(
    private val settings: TerminalSettings,
    fontAvailable: (String) -> Boolean,
) : DefaultSettingsProvider() {
    private val font: Font = buildFont(fontAvailable)

    override fun altSendsEscape(): Boolean = settings.optionAsMeta

    override fun getTerminalFont(): Font = font

    override fun getBufferMaxLinesCount(): Int = settings.scrollbackLines

    override fun copyOnSelect(): Boolean = false

    override fun useAntialiasing(): Boolean = true

    override fun getTerminalFontSize(): Float = settings.fontSize.toFloat()

    override fun emulateX11CopyPaste(): Boolean = false

    /** No shortcut, so Cmd-K stays with the app's palette (ADR 0012 decision 3). */
    override fun getClearBufferActionPresentation(): TerminalActionPresentation =
        TerminalActionPresentation("Clear Buffer", emptyList<KeyStroke>())

    /** No shortcut: Cmd-F belongs to the app's own search bar (AM-504). */
    override fun getFindActionPresentation(): TerminalActionPresentation =
        TerminalActionPresentation("Find", emptyList<KeyStroke>())

    private fun buildFont(fontAvailable: (String) -> Boolean): Font {
        val size = settings.fontSize.toFloat()
        val family = settings.fontFamily?.takeIf(fontAvailable)
        val base = if (family != null) Font(family, Font.PLAIN, settings.fontSize) else super.getTerminalFont()
        val sized = base.deriveFont(size)
        return if (settings.ligatures) {
            sized.deriveFont(mapOf(TextAttribute.LIGATURES to TextAttribute.LIGATURES_ON))
        } else {
            sized
        }
    }
}
