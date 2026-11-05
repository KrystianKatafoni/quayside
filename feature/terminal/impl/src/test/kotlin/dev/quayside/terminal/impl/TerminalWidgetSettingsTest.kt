package dev.quayside.terminal.impl

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.TerminalColors
import dev.quayside.terminal.api.TerminalSettings
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.font.TextAttribute
import java.util.stream.Stream

/**
 * The widget settings of docs/plans/AM-501.md and AM-502 (Cmd-K released), now driven by [TerminalSettings] (AM-508):
 * `optionAsMeta` is off by default (ADR 0016), so Option types characters such as `ą`.
 */
class TerminalWidgetSettingsTest {
    private fun widget(
        settings: TerminalSettings = TerminalSettings(),
        colors: TerminalColors = COLORS,
        fontAvailable: (String) -> Boolean = { true },
    ) = TerminalWidgetSettings(settings, colors, fontAvailable)

    private val settings = widget()

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("flags")
    fun `ustawienia widzetu maja wartosci z planu`(
        name: String,
        expected: Boolean,
    ) {
        val actual =
            when (name) {
                "altSendsEscape" -> settings.altSendsEscape()
                "copyOnSelect" -> settings.copyOnSelect()
                "useAntialiasing" -> settings.useAntialiasing()
                "emulateX11CopyPaste" -> settings.emulateX11CopyPaste()
                else -> error("unknown setting $name")
            }

        actual shouldBe expected
    }

    @Test
    fun `rozmiar fontu to 13`() {
        settings.terminalFontSize shouldBe 13f
    }

    @Test
    fun `font size follows settings`() {
        val custom = widget(TerminalSettings(fontSize = 18))

        custom.terminalFontSize shouldBe 18f
        custom.terminalFont.size shouldBe 18
    }

    @Test
    fun `scrollback follows settings`() {
        widget(TerminalSettings(scrollbackLines = 5_000)).bufferMaxLinesCount shouldBe 5_000
        settings.bufferMaxLinesCount shouldBe 100_000
    }

    @Test
    fun `option as meta off sends no escape`() {
        widget(TerminalSettings(optionAsMeta = false)).altSendsEscape() shouldBe false
    }

    @Test
    fun `option as meta on sends escape`() {
        widget(TerminalSettings(optionAsMeta = true)).altSendsEscape() shouldBe true
    }

    @Test
    fun `ligatures on set the ligature attribute`() {
        val font = widget(TerminalSettings(ligatures = true)).terminalFont

        font.attributes[TextAttribute.LIGATURES] shouldBe TextAttribute.LIGATURES_ON
    }

    @Test
    fun `ligatures off leave it unset`() {
        val font = widget(TerminalSettings(ligatures = false)).terminalFont

        font.attributes[TextAttribute.LIGATURES] shouldBe null
    }

    @Test
    fun `installed font family is used`() {
        val custom = widget(TerminalSettings(fontFamily = "Serif"), fontAvailable = { it == "Serif" })

        custom.terminalFont.family shouldBe "Serif"
    }

    @Test
    fun `missing font family falls back to the default font`() {
        val custom = widget(TerminalSettings(fontFamily = "No Such Font"), fontAvailable = { false })

        custom.terminalFont.family shouldBe DefaultSettingsProvider().terminalFont.family
    }

    @Test
    fun `default foreground is termText`() {
        settings.defaultForeground shouldBe TerminalColor.rgb(0xE0, 0xD0, 0xC0)
    }

    @Test
    fun `default background is termBg`() {
        settings.defaultBackground shouldBe TerminalColor.rgb(0x10, 0x20, 0x30)
    }

    @Test
    fun `colours follow the snapshot given at build`() {
        val other = TerminalColors(termBg = Rgba(0xFF010203), termText = Rgba(0xFF0A0B0C))
        val custom = widget(colors = other)

        custom.defaultBackground shouldBe TerminalColor.rgb(1, 2, 3)
        custom.defaultForeground shouldBe TerminalColor.rgb(10, 11, 12)
    }

    @Test
    fun `clearBufferActionPresentation keyStrokes is empty so Cmd-K stays free`() {
        settings.clearBufferActionPresentation.keyStrokes shouldBe emptyList()
    }

    @Test
    fun `find action has no shortcut`() {
        settings.findActionPresentation.keyStrokes shouldBe emptyList()
    }

    companion object {
        private val COLORS = TerminalColors(termBg = Rgba(0xFF102030), termText = Rgba(0xFFE0D0C0))

        @JvmStatic
        fun flags(): Stream<Arguments> =
            Stream.of(
                Arguments.of("altSendsEscape", false),
                Arguments.of("copyOnSelect", false),
                Arguments.of("useAntialiasing", true),
                Arguments.of("emulateX11CopyPaste", false),
            )
    }
}
