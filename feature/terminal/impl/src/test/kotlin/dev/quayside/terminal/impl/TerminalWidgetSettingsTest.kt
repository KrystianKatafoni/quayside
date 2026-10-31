package dev.quayside.terminal.impl

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** The settings table of docs/plans/AM-501.md (Option as Meta from docs/06) and Cmd-K released (AM-502). */
class TerminalWidgetSettingsTest {
    private val settings = TerminalWidgetSettings()

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
    fun `clearBufferActionPresentation keyStrokes is empty so Cmd-K stays free`() {
        settings.clearBufferActionPresentation.keyStrokes shouldBe emptyList()
    }

    @Test
    fun `find action has no shortcut`() {
        settings.findActionPresentation.keyStrokes shouldBe emptyList()
    }

    companion object {
        @JvmStatic
        fun flags(): Stream<Arguments> =
            Stream.of(
                Arguments.of("altSendsEscape", true),
                Arguments.of("copyOnSelect", false),
                Arguments.of("useAntialiasing", true),
                Arguments.of("emulateX11CopyPaste", false),
            )
    }
}
