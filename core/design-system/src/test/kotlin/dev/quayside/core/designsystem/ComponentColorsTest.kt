package dev.quayside.core.designsystem

import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.mix
import dev.quayside.core.designtokens.over
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** A named colour scheme; [toString] is the test display name. */
data class Scheme(
    val name: String,
    val tokens: ColorTokens,
) {
    override fun toString(): String = name
}

/** A row of the button table: [kind] and the colours it must give on plex. */
internal data class ButtonRow(
    val kind: ButtonKind,
    val expected: (ColorTokens) -> ButtonColors,
) {
    override fun toString(): String = kind.name
}

/**
 * Colour pairs of the components on a dark (`plex`) and a light (`paper`) scheme; the components read their colours
 * only from these functions, so every pair on screen is covered. Pairs are (background, foreground) as in the plan.
 */
internal class ComponentColorsTest {
    private fun contrast(
        foreground: Rgba,
        background: Rgba,
    ): Double = Wcag.contrast(foreground, background)

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `primary button label meets 4_5 on accent`(scheme: Scheme) {
        val t = scheme.tokens
        val (background, _, label) = buttonColors(ButtonKind.PRIMARY, enabled = true, tokens = t)

        background shouldBe t.content.accent
        label shouldBe t.content.accentInk
        contrast(label, background) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `secondary button label meets 4_5 on raised`(scheme: Scheme) {
        val t = scheme.tokens
        val (background, _, label) = buttonColors(ButtonKind.SECONDARY, enabled = true, tokens = t)

        background shouldBe t.surfaces.raised
        label shouldBe t.content.text
        contrast(label, background) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `selected row text meets 4_5 on sel`(scheme: Scheme) {
        val t = scheme.tokens
        val (background, text) = rowColors(selected = true, tokens = t)

        background shouldBe t.surfaces.sel
        text shouldBe t.content.text
        contrast(text, background) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `unselected row text meets 4_5 on its background`(scheme: Scheme) {
        val t = scheme.tokens
        val (background, text) = rowColors(selected = false, tokens = t)

        (background == t.surfaces.sel) shouldBe false
        contrast(text, background.over(t.surfaces.panel.over(t.surfaces.bg))) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `mode bar text meets 4_5 on panel`(scheme: Scheme) {
        val t = scheme.tokens
        val (background, text) = modeBarColors(t)

        background shouldBe t.surfaces.panel
        text shouldBe t.content.muted
        contrast(text, background.over(t.surfaces.bg)) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `mode badge label meets 4_5 on accent`(scheme: Scheme) {
        val t = scheme.tokens
        val (background, label) = badgeColors(t)

        background shouldBe t.content.accent
        label shouldBe t.content.accentInk
        contrast(label, background) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `text meets 4_5 on panel and raised fill`(scheme: Scheme) {
        val t = scheme.tokens
        val base = t.surfaces.bg

        val (panelFill, panelBorder) = panelColors(raised = false, tokens = t)
        val (raisedFill, raisedBorder) = panelColors(raised = true, tokens = t)

        panelFill shouldBe t.surfaces.panel
        raisedFill shouldBe t.surfaces.raised
        panelBorder shouldBe t.surfaces.border
        raisedBorder shouldBe t.surfaces.border
        contrast(t.content.text, panelFill.over(base)) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
        contrast(t.content.text, raisedFill.over(base)) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    /**
     * Borders are decoration: a button is identified by its label and fill, a panel by its fill, the mode bar by its
     * text. So NFR-6 gives them no 3:1 requirement (WCAG 1.4.11 applies only to the sole indicator of a component);
     * the test pins the token mapping and that a border can be told from the fill it is drawn on.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `borders map to border tokens and differ from their fill`(scheme: Scheme) {
        val t = scheme.tokens

        buttonBorder(t) shouldBe t.surfaces.border2
        modeBarBorder(t) shouldBe t.surfaces.border
        (buttonBorder(t) == buttonColors(ButtonKind.SECONDARY, enabled = true, tokens = t).background) shouldBe false
        (modeBarBorder(t) == modeBarColors(t).first) shouldBe false
        (panelColors(raised = false, tokens = t).let { it.first == it.second }) shouldBe false
        (panelColors(raised = true, tokens = t).let { it.first == it.second }) shouldBe false
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `focus frame meets 3 to 1 on bg panel raised and sel`(scheme: Scheme) {
        val t = scheme.tokens
        val frame = focusColor(t)
        val panel = t.surfaces.panel.over(t.surfaces.bg)

        frame shouldBe t.content.text
        listOf(t.surfaces.bg, panel, t.surfaces.raised, t.surfaces.sel).forEach {
            contrast(frame, it) shouldBeGreaterThanOrEqual Wcag.NON_TEXT_MIN
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `disabled button label is dim for every kind`(scheme: Scheme) {
        val t = scheme.tokens
        ButtonKind.entries.forEach { kind ->
            buttonColors(kind, enabled = false, tokens = t).label shouldBe t.content.dim
            // enabled again: not dim, so the test cannot pass by a constant
            (buttonColors(kind, enabled = true, tokens = t).label == t.content.dim) shouldBe false
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `dropdown field meets 4_5 on raised`(scheme: Scheme) {
        val t = scheme.tokens
        val (background, label) = dropdownFieldColors(enabled = true, tokens = t)

        background shouldBe t.surfaces.raised
        label shouldBe t.content.text
        contrast(label, background) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `dropdown field is the pair of a secondary button`(scheme: Scheme) {
        val t = scheme.tokens
        val button = buttonColors(ButtonKind.SECONDARY, enabled = true, tokens = t)

        dropdownFieldColors(enabled = true, tokens = t) shouldBe (button.background to button.label)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `disabled dropdown field uses dim`(scheme: Scheme) {
        val t = scheme.tokens

        dropdownFieldColors(enabled = false, tokens = t) shouldBe (t.surfaces.raised to t.content.dim)
        // enabled again: not dim, so the test cannot pass by a constant
        (dropdownFieldColors(enabled = true, tokens = t).second == t.content.dim) shouldBe false
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buttonTable")
    fun `button colours per kind`(row: ButtonRow) {
        val t = SampleTokens.colors()
        val colors = buttonColors(row.kind, enabled = true, tokens = t)

        colors shouldBe row.expected(t)
    }

    @Test
    fun `danger label is err when it meets 4_5 on panel`() {
        val t = SampleTokens.colors()
        val panel = t.surfaces.panel.over(t.surfaces.bg)

        contrast(t.status.err, panel) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
        buttonColors(ButtonKind.DANGER, enabled = true, tokens = t).label shouldBe t.status.err
    }

    @Test
    fun `danger label falls back to text below 4_5`() {
        // Given an err colour that is close to the panel
        val base = SampleTokens.colors()
        val t = base.copy(status = base.status.copy(err = SampleTokens.rgb(0x24202C)))
        val panel = t.surfaces.panel.over(t.surfaces.bg)
        contrast(t.status.err, panel) shouldBeLessThan Wcag.TEXT_MIN

        val colors = buttonColors(ButtonKind.DANGER, enabled = true, tokens = t)

        colors.label shouldBe t.content.text
        colors.border shouldBe expectedDangerBorder(t)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `danger label meets 4_5 on panel and has no fill`(scheme: Scheme) {
        val t = scheme.tokens
        val colors = buttonColors(ButtonKind.DANGER, enabled = true, tokens = t)

        colors.background.alpha shouldBe 0
        contrast(colors.label, t.surfaces.panel.over(t.surfaces.bg)) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    companion object {
        @JvmStatic
        fun schemes(): Stream<Scheme> = Stream.of(Scheme("plex", SampleTokens.colors()), Scheme("paper", paperColors()))

        @JvmStatic
        fun buttonTable(): Stream<ButtonRow> =
            Stream.of(
                ButtonRow(ButtonKind.PRIMARY) { ButtonColors(it.content.accent, null, it.content.accentInk) },
                ButtonRow(ButtonKind.SECONDARY) {
                    ButtonColors(it.surfaces.raised, it.surfaces.border2, it.content.text)
                },
                ButtonRow(ButtonKind.DANGER) {
                    ButtonColors(NO_FILL, expectedDangerBorder(it), it.status.err)
                },
            )

        /** `tinted(err)` at 35 percent: panel over bg, mixed with err (the border of the danger icon button). */
        private fun expectedDangerBorder(t: ColorTokens): Rgba =
            t.surfaces.panel
                .over(t.surfaces.bg)
                .mix(t.status.err, 35)

        private val NO_FILL = Rgba(0x00000000L)
    }
}
