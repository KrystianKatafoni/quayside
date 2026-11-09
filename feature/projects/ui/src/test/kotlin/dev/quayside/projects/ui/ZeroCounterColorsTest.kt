package dev.quayside.projects.ui

import dev.quayside.core.designsystem.StatusTone
import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.over
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** One row of the table: the input of [zeroCounterColors] and the colours the plan (AM-310) demands. */
internal data class ZeroCase(
    val title: String,
    val tone: StatusTone,
    val selected: Boolean,
    val tokens: ColorTokens,
    val expected: ZeroCounterPaint,
) {
    override fun toString(): String = title
}

/**
 * The colours of a zero counter (FR-P6, NFR-6, AM-310): ring = the status token of the tone, fill = the background of
 * the row (opaque: sel when selected, else panel over bg), number = muted, or text when muted has less than 4.5:1 on
 * the fill. `projects:ui` and `mission-control:ui` hold copies of the rule; the same table runs in both modules, so
 * the copies cannot drift apart.
 */
internal class ZeroCounterColorsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `zero counter colours follow the tokens`(case: ZeroCase) {
        zeroCounterColors(case.tone, case.selected, case.tokens) shouldBe case.expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    fun `label contrast is at least 4,5 to 1 on panel and sel for every tone`(
        @Suppress("UNUSED_PARAMETER") name: String,
        tokens: ColorTokens,
    ) {
        TONES.forEach { tone ->
            listOf(true, false).forEach { selected ->
                val colors = zeroCounterColors(tone, selected, tokens)
                Wcag.contrast(colors.label, colors.fill) shouldBeGreaterThanOrEqualTo Wcag.TEXT_MIN
            }
        }
    }

    @Test
    fun `the tones have four different rings in the sample theme`() {
        // A guard for the table itself: if two status tokens were equal, "swapped tokens" could not be told apart
        TONES.map { ring(it, SampleTokens.colors()) }.toSet().size shouldBe TONES.size
    }

    companion object {
        private val TONES = listOf(StatusTone.WARN, StatusTone.WORK, StatusTone.OK, StatusTone.ERR)
        private val plex = SampleTokens.colors()

        /** bg and panel differ and the panel is half transparent: the fill is the composite, not the panel. */
        private val translucent =
            plex.copy(surfaces = plex.surfaces.copy(panel = Rgba(0x803A3A60L), bg = SampleTokens.rgb(0x0B0A10)))

        /** Grey 0x888888: 4.1:1 on sel (below 4.5), 5.4:1 on panel. */
        private val mutedLowOnSel = withMuted(SampleTokens.rgb(0x888888))

        /** Muted almost as dark as the panel and sel: below 4.5:1 on both. */
        private val mutedLowEverywhere = withMuted(SampleTokens.rgb(0x30303C))

        private fun withMuted(muted: Rgba): ColorTokens = plex.copy(content = plex.content.copy(muted = muted))

        private fun ring(
            tone: StatusTone,
            tokens: ColorTokens,
        ): Rgba =
            when (tone) {
                StatusTone.WARN -> tokens.status.warn
                StatusTone.WORK -> tokens.status.work
                StatusTone.OK -> tokens.status.ok
                StatusTone.ERR -> tokens.status.err
                else -> error("no zero counter for $tone")
            }

        private fun fill(
            selected: Boolean,
            tokens: ColorTokens,
        ): Rgba = if (selected) tokens.surfaces.sel else tokens.surfaces.panel.over(tokens.surfaces.bg)

        /** The four tones, unselected and selected, of one theme; [onPanel] and [onSel] are the expected labels. */
        private fun rows(
            theme: String,
            tokens: ColorTokens,
            onPanel: Rgba,
            onSel: Rgba,
        ): List<Arguments> =
            TONES.flatMap { tone ->
                listOf(false, true).map { selected ->
                    val label = if (selected) onSel else onPanel
                    val colors = ZeroCounterPaint(fill(selected, tokens), ring(tone, tokens), label)
                    arguments(ZeroCase("$theme, $tone, selected=$selected", tone, selected, tokens, colors))
                }
            }

        @JvmStatic
        fun cases(): List<Arguments> =
            rows("plex", plex, plex.content.muted, plex.content.muted) +
                rows("translucent panel", translucent, translucent.content.muted, translucent.content.muted) +
                rows("muted low on sel", mutedLowOnSel, mutedLowOnSel.content.muted, mutedLowOnSel.content.text) +
                rows(
                    "muted low everywhere",
                    mutedLowEverywhere,
                    mutedLowEverywhere.content.text,
                    mutedLowEverywhere.content.text,
                )

        @JvmStatic
        fun themes(): List<Arguments> =
            listOf(
                arguments("plex", plex),
                arguments("translucent panel", translucent),
                arguments("muted low on sel", mutedLowOnSel),
                arguments("muted low everywhere", mutedLowEverywhere),
            )
    }
}
