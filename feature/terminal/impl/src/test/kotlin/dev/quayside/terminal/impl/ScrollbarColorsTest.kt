package dev.quayside.terminal.impl

import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.TerminalColors
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Scroll bar colours of docs/plans/AM-810.md: background termBg, track termBg + 6 % termText, thumb the smallest
 * mix of 45..100 % with 3:1 on the track and on termBg. Expected hex values are computed by hand from the
 * channel-wise mix (round(from + (to - from) * p / 100)) and the WCAG contrast, not by the code under test.
 */
class ScrollbarColorsTest {
    private fun derive(
        bg: Long,
        text: Long,
    ) = scrollbarColors(TerminalColors(termBg = Rgba(bg), termText = Rgba(text), ansi = TEST_ANSI))

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("baseRows", "raisedRows")
    fun `background is termBg`(
        bg: Long,
        text: Long,
    ) {
        derive(bg, text).background shouldBe Rgba(bg)
    }

    @ParameterizedTest(name = "track on {0} and {1} is {2}")
    @MethodSource("trackRows")
    fun `track is termBg mixed with termText 6 percent`(
        bg: Long,
        text: Long,
        track: Long,
    ) {
        derive(bg, text).track shouldBe Rgba(track)
        derive(bg, text).track shouldBe Rgba(bg).mix(Rgba(text), 6)
    }

    @ParameterizedTest(name = "thumb on {0} and {1} is {2}")
    @MethodSource("thumbRows")
    fun `thumb is the smallest mix that meets 3 to 1`(
        bg: Long,
        text: Long,
        thumb: Long,
    ) {
        derive(bg, text).thumb shouldBe Rgba(thumb)
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("baseRows")
    fun `base mix is kept when it meets 3 to 1`(
        bg: Long,
        text: Long,
    ) {
        derive(bg, text).thumb shouldBe Rgba(bg).mix(Rgba(text), 45)
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("raisedRows")
    fun `thumb is raised until it meets 3 to 1`(
        bg: Long,
        text: Long,
    ) {
        val colors = derive(bg, text)
        val percent = (45..100).first { Rgba(bg).mix(Rgba(text), it) == colors.thumb }

        percent shouldBeGreaterThan 45
        Wcag.contrast(colors.thumb, colors.track) shouldBeGreaterThanOrEqualTo 3.0
        Wcag.contrast(colors.thumb, colors.background) shouldBeGreaterThanOrEqualTo 3.0
        // One percentage point less fails on the track or on the background: the result is minimal.
        val lower = Rgba(bg).mix(Rgba(text), percent - 1)
        minOf(Wcag.contrast(lower, colors.track), Wcag.contrast(lower, colors.background)) shouldBeLessThan 3.0
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("lightRows")
    fun `thumb meets 3 to 1 on a light terminal`(
        bg: Long,
        text: Long,
    ) {
        val colors = derive(bg, text)

        Wcag.contrast(colors.thumb, colors.track) shouldBeGreaterThanOrEqualTo Wcag.NON_TEXT_MIN
        Wcag.contrast(colors.thumb, colors.background) shouldBeGreaterThanOrEqualTo Wcag.NON_TEXT_MIN
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("lightRows")
    fun `thumb is darker than the track on a light terminal`(
        bg: Long,
        text: Long,
    ) {
        val colors = derive(bg, text)

        Wcag.luminance(colors.thumb) shouldBeLessThan Wcag.luminance(colors.track)
        Wcag.luminance(colors.track) shouldBeLessThan Wcag.luminance(colors.background)
    }

    @Test
    fun `alpha of tokens is dropped`() {
        val translucent = derive(0x80102030L, 0x40E0D0C0L)

        translucent shouldBe derive(0xFF102030L, 0xFFE0D0C0L)
        translucent.background.isOpaque shouldBe true
        translucent.track.isOpaque shouldBe true
        translucent.thumb.isOpaque shouldBe true
    }

    @Test
    fun `thumb falls back to termText when no mix reaches 3 to 1`() {
        // 1.66:1 text on black: no mix reaches 3:1, text differs from the background.
        derive(0xFF000000L, 0xFF333333L).thumb shouldBe Rgba(0xFF333333)
    }

    @Test
    fun `thumb meets 3 to 1 on track and termBg whenever termText meets 4_5`() {
        val failures = mutableListOf<String>()
        var checked = 0
        for (bg in backgrounds()) {
            for (text in texts()) {
                if (Wcag.contrast(text, bg) < Wcag.TEXT_MIN) continue
                val colors = scrollbarColors(TerminalColors(termBg = bg, termText = text, ansi = TEST_ANSI))
                val onTrack = Wcag.contrast(colors.thumb, colors.track)
                val onBackground = Wcag.contrast(colors.thumb, colors.background)
                if (onTrack < Wcag.NON_TEXT_MIN || onBackground < Wcag.NON_TEXT_MIN) {
                    failures += "bg ${bg.argb.toString(HEX)} text ${text.argb.toString(HEX)}: $onTrack, $onBackground"
                }
                checked++
            }
        }

        failures shouldBe emptyList()
        checked shouldBeGreaterThan MIN_PAIRS
    }

    private fun backgrounds(): List<Rgba> {
        val dark = (0x00..0x60 step 8).map { grey(it) }
        val light = (0xA0..0xFF step 16).map { grey(it) }
        val tinted = listOf(0xFF002B36L, 0xFF2E3440L, 0xFF22272EL, 0xFFFDF6E3L).map { Rgba(it) }
        return dark + light + tinted
    }

    private fun texts(): List<Rgba> {
        val greys = (0x00..0xFF step 16).map { grey(it) }
        val coloured =
            listOf(255, 192, 128).flatMap { level -> HUES.map { (r, g, b) -> rgb(r * level, g * level, b * level) } }
        return greys + coloured
    }

    private fun grey(level: Int): Rgba = rgb(level, level, level)

    private fun rgb(
        r: Int,
        g: Int,
        b: Int,
    ): Rgba = Rgba(0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())

    /** Expected derivation of one pair of terminal colours. */
    data class Row(
        val bg: Long,
        val text: Long,
        val track: Long,
        val thumb: Long,
    )

    companion object {
        private const val HEX = 16
        private const val MIN_PAIRS = 100
        private val HUES =
            listOf(
                Triple(1, 0, 0),
                Triple(1, 1, 0),
                Triple(0, 1, 0),
                Triple(0, 1, 1),
                Triple(0, 0, 1),
                Triple(1, 0, 1),
            )

        @JvmStatic
        fun trackRows(): Stream<Arguments> = rows().map { Arguments.of(it.bg, it.text, it.track) }

        @JvmStatic
        fun thumbRows(): Stream<Arguments> = rows().map { Arguments.of(it.bg, it.text, it.thumb) }

        // bg, text, track, thumb. Different bg and text in every row, so a swapped argument changes the result.
        private fun rows(): Stream<Row> =
            Stream.of(
                // plex: base 45 % is enough
                Row(0xFF0B0A10L, 0xFFE8E5F2L, 0xFF18171EL, 0xFF6E6D76L),
                // contrast theme
                Row(0xFF000000L, 0xFFFFFFFFL, 0xFF0F0F0FL, 0xFF737373L),
                // light themes: dark terminal colours
                Row(0xFF1E242CL, 0xFFE2E8F0L, 0xFF2A3038L, 0xFF767C84L),
                // boundary 4.56:1: raised to 79 %
                Row(0xFF000000L, 0xFF757575L, 0xFF070707L, 0xFF5C5C5CL),
                // raised to 47 %
                Row(0xFF102030L, 0xFFE0D0C0L, 0xFF1C2B39L, 0xFF727374L),
            )

        @JvmStatic
        fun baseRows(): Stream<Arguments> =
            Stream.of(
                Arguments.of(0xFF0B0A10L, 0xFFE8E5F2L),
                Arguments.of(0xFF000000L, 0xFFFFFFFFL),
                Arguments.of(0xFF1E242CL, 0xFFE2E8F0L),
            )

        // Light themes since AM-811: paper, a white theme with black text, a warm paper theme.
        @JvmStatic
        fun lightRows(): Stream<Arguments> =
            Stream.of(
                Arguments.of(0xFFF4F6F8L, 0xFF2D3748L),
                Arguments.of(0xFFFFFFFFL, 0xFF000000L),
                Arguments.of(0xFFFDFBF7L, 0xFF3A3832L),
            )

        @JvmStatic
        fun raisedRows(): Stream<Arguments> =
            Stream.of(
                Arguments.of(0xFF000000L, 0xFF757575L),
                Arguments.of(0xFF102030L, 0xFFE0D0C0L),
            )
    }
}
