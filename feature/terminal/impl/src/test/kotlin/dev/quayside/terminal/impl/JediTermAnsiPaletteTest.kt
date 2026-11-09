package dev.quayside.terminal.impl

import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.SettingsProvider
import dev.quayside.core.designtokens.AnsiColors
import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.terminal.api.TerminalSession
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The ANSI palette of the theme reaches the real widget (AM-811, ADR 0027): read from the settings provider of the
 * widget (the pattern of the colours test of AM-807). A pixel test of red output was left out: the widget is not
 * laid out without a window, so painting it is unstable. Need a display.
 */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class JediTermAnsiPaletteTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var kit: TerminalTestKit

    @BeforeEach
    fun setUp() {
        assumeFalse(TerminalTestKit.headless, "JediTermWidget needs a display")
        kit = TerminalTestKit(dir, RecordingLogger())
    }

    @AfterEach
    fun tearDown() {
        if (this::kit.isInitialized) kit.close()
    }

    @Test
    fun `ansi palette changed after open applies to the next tab only`() =
        runTest {
            val first = kit.openShell()
            val changed = TerminalTestKit.DEFAULT_COLORS.copy(ansi = AnsiColors(TEST_ANSI.colors.reversed()))
            kit.colors.value = changed
            val second = kit.openShell()

            first.paletteRgb(0) shouldBe TEST_ANSI[0].argb.toInt()
            second.paletteRgb(0) shouldBe changed.ansi[0].argb.toInt()
            second.paletteRgb(0) shouldBe TEST_ANSI[15].argb.toInt()
            first.paletteRgb(1) shouldBe TEST_ANSI[1].argb.toInt()
            second.paletteRgb(1) shouldBe TEST_ANSI[14].argb.toInt()
        }

    private suspend fun TerminalTestKit.openShell(): TerminalSession =
        api
            .openShell(dir)
            .shouldBeInstanceOf<Outcome.Ok<TerminalSession>>()
            .value

    /** Foreground RGB of palette index [index] in the settings provider this widget was built with. */
    private fun TerminalSession.paletteRgb(index: Int): Int {
        val field = JediTermWidget::class.java.getDeclaredField("mySettingsProvider").apply { isAccessible = true }
        val provider = field.get(widget()) as SettingsProvider
        val palette: ColorPalette = provider.terminalColorPalette
        return palette.foregroundRgb(index)
    }
}
