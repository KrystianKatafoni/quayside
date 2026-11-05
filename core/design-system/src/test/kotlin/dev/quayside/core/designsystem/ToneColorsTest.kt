package dev.quayside.core.designsystem

import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.SampleTokens.rgb
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import dev.quayside.core.designtokens.over
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** Pure colour rules of tones, chips, icon buttons, counter badge and key cap (plan AM-808), on plex and paper. */
class ToneColorsTest {
    private val plex = SampleTokens.colors()
    private val paper = paperColors()

    private fun contrast(
        foreground: Rgba,
        background: Rgba,
    ): Double = Wcag.contrast(foreground, background)

    /** Rule of the plan: `tinted(c) = panel.over(bg).mix(c, 15)`. */
    private fun tinted(
        tone: Rgba,
        t: ColorTokens,
    ): Rgba =
        t.surfaces.panel
            .over(t.surfaces.bg)
            .mix(tone, 15)

    private fun fill(t: ColorTokens): Rgba = t.surfaces.panel.over(t.surfaces.bg)

    @Test
    fun `tone colour maps to its token`() {
        // The six tone colours of plex are pairwise different, so a swapped mapping cannot hide
        val expected =
            mapOf(
                StatusTone.NEUTRAL to plex.content.muted,
                StatusTone.ACCENT to plex.content.accent,
                StatusTone.WARN to plex.status.warn,
                StatusTone.WORK to plex.status.work,
                StatusTone.OK to plex.status.ok,
                StatusTone.ERR to plex.status.err,
            )
        expected.values.toSet().size shouldBe StatusTone.entries.size

        expected.forEach { (tone, color) -> toneColor(tone, plex) shouldBe color }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(StatusTone::class, names = ["ACCENT", "WORK", "OK", "ERR"])
    fun `tinted background mixes panel over bg with 15 percent of the tone`(tone: StatusTone) {
        // Given a translucent panel over a different bg: only `over` first gives the expected colour
        val t =
            plex.copy(
                surfaces = plex.surfaces.copy(bg = rgb(0x203040), panel = rgb(0x8090A0).withAlpha(50)),
            )

        toneBackground(tone, t) shouldBe tinted(toneColor(tone, t), t)
    }

    @Test
    fun `tinted background of ok on plex is the literal mix`() {
        // 100E17 mixed with 15 percent of 86EFAC, computed by hand
        toneBackground(StatusTone.OK, plex) shouldBe rgb(0x22302D)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `warn background uses warnBg`(scheme: Scheme) {
        toneBackground(StatusTone.WARN, scheme.tokens) shouldBe scheme.tokens.status.warnBg
        // warnBg that is not the tinted warn: the token wins over the rule
        val custom = scheme.tokens.copy(status = scheme.tokens.status.copy(warnBg = rgb(0x123456)))
        toneBackground(StatusTone.WARN, custom) shouldBe rgb(0x123456)
    }

    @Test
    fun `neutral background is raised`() {
        toneBackground(StatusTone.NEUTRAL, plex) shouldBe plex.surfaces.raised
        toneBackground(StatusTone.NEUTRAL, paper) shouldBe paper.surfaces.raised
    }

    @Test
    fun `chip label uses the tone when it meets 4_5`() {
        val colors = chipColors(StatusTone.WARN, plex)

        contrast(plex.status.warn, plex.status.warnBg) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
        colors.label shouldBe plex.status.warn
        colors.mark shouldBe plex.status.warn
        colors.background shouldBe plex.status.warnBg
    }

    @Test
    fun `neutral chip uses text for the label and muted for the mark`() {
        val colors = chipColors(StatusTone.NEUTRAL, plex)

        colors.label shouldBe plex.content.text
        colors.mark shouldBe plex.content.muted
        colors.background shouldBe plex.surfaces.raised
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(StatusTone::class, names = ["WARN", "OK"])
    fun `chip label falls back to text below 4_5`(tone: StatusTone) {
        // Given paper, where warn and ok have less than 4.5:1 on their own tinted background
        val background = toneBackground(tone, paper)
        contrast(toneColor(tone, paper), background) shouldBeLessThan Wcag.TEXT_MIN

        val colors = chipColors(tone, paper)

        colors.label shouldBe paper.content.text
        // and the mark keeps the tone, because it still meets 3:1
        colors.mark shouldBe toneColor(tone, paper)
        contrast(colors.label, background) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    @Test
    fun `chip mark falls back to text below 3 to 1`() {
        // Given a warn colour almost equal to its background
        val t = plex.copy(status = plex.status.copy(warn = rgb(0x403020)))
        contrast(t.status.warn, t.status.warnBg) shouldBeLessThan Wcag.NON_TEXT_MIN

        val colors = chipColors(StatusTone.WARN, t)

        colors.mark shouldBe t.content.text
        colors.label shouldBe t.content.text
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `chip label meets 4_5 and mark 3 to 1 for every tone`(scheme: Scheme) {
        StatusTone.entries.forEach { tone ->
            val colors = chipColors(tone, scheme.tokens)

            colors.background shouldBe toneBackground(tone, scheme.tokens)
            contrast(colors.label, colors.background) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
            contrast(colors.mark, colors.background) shouldBeGreaterThanOrEqual Wcag.NON_TEXT_MIN
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `icon button colours per style`(scheme: Scheme) {
        val t = scheme.tokens
        val danger = fill(t).mix(t.status.err, 35)

        fun colors(style: IconButtonStyle) = iconButtonColors(IconButtonOptions(style = style), t)

        colors(IconButtonStyle.GHOST).background.alpha shouldBe 0
        colors(IconButtonStyle.GHOST).border shouldBe null
        colors(IconButtonStyle.GHOST).icon shouldBe t.content.muted
        colors(IconButtonStyle.OUTLINED) shouldBe
            IconButtonColors(t.surfaces.raised, t.surfaces.border2, t.content.text)
        colors(IconButtonStyle.SELECTED) shouldBe IconButtonColors(t.surfaces.sel, t.surfaces.border2, t.content.text)
        colors(IconButtonStyle.DANGER).background.alpha shouldBe 0
        colors(IconButtonStyle.DANGER).border shouldBe danger
        colors(IconButtonStyle.DANGER).icon shouldBe t.status.err
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(IconButtonStyle::class, names = ["GHOST", "OUTLINED", "SELECTED"])
    fun `tone colours the icon of a non danger button`(style: IconButtonStyle) {
        val options = IconButtonOptions(style = style, tone = StatusTone.WARN)

        iconButtonColors(options, plex).icon shouldBe plex.status.warn
    }

    @Test
    fun `danger ignores the tone`() {
        val options = IconButtonOptions(style = IconButtonStyle.DANGER, tone = StatusTone.OK)

        iconButtonColors(options, plex).icon shouldBe plex.status.err
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(IconButtonStyle::class)
    fun `disabled icon button uses dim`(style: IconButtonStyle) {
        val options = IconButtonOptions(enabled = false, style = style, tone = StatusTone.WARN)

        iconButtonColors(options, plex).icon shouldBe plex.content.dim
        // enabled again: not dim, so the test cannot pass by a constant
        iconButtonColors(options.copy(enabled = true), plex).icon shouldBe
            (if (style == IconButtonStyle.DANGER) plex.status.err else plex.status.warn)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `counter badge is warnInk on warn and meets 4_5`(scheme: Scheme) {
        val (background, ink) = counterBadgeColors(scheme.tokens)

        background shouldBe scheme.tokens.status.warn
        ink shouldBe scheme.tokens.status.warnInk
        contrast(ink, background) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `key cap text meets 4_5 on panel and raised`(scheme: Scheme) {
        val t = scheme.tokens
        val (text, border) = keyCapColors(t)

        text shouldBe t.content.muted
        border shouldBe t.surfaces.border2
        contrast(text, fill(t)) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
        contrast(text, t.surfaces.raised) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    companion object {
        @JvmStatic
        fun schemes(): Stream<Scheme> = Stream.of(Scheme("plex", SampleTokens.colors()), Scheme("paper", paperColors()))
    }
}
