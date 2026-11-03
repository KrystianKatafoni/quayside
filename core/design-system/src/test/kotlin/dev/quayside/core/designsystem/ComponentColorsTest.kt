package dev.quayside.core.designsystem

import dev.quayside.core.designtokens.ColorTokens
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.designtokens.Wcag
import dev.quayside.core.designtokens.over
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
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

/**
 * Colour pairs of the components on a dark (`plex`) and a light (`paper`) scheme; the components read their colours
 * only from these functions, so every pair on screen is covered. Pairs are (background, foreground) as in the plan.
 */
class ComponentColorsTest {
    private fun contrast(
        foreground: Rgba,
        background: Rgba,
    ): Double = Wcag.contrast(foreground, background)

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `primary button label meets 4_5 on accent`(scheme: Scheme) {
        val t = scheme.tokens
        val (background, label) = buttonColors(ButtonKind.PRIMARY, enabled = true, tokens = t)

        background shouldBe t.content.accent
        label shouldBe t.content.accentInk
        contrast(label, background) shouldBeGreaterThanOrEqual Wcag.TEXT_MIN
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("schemes")
    fun `secondary button label meets 4_5 on raised`(scheme: Scheme) {
        val t = scheme.tokens
        val (background, label) = buttonColors(ButtonKind.SECONDARY, enabled = true, tokens = t)

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
        (buttonBorder(t) == buttonColors(ButtonKind.SECONDARY, enabled = true, tokens = t).first) shouldBe false
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
    fun `disabled button uses dim label`(scheme: Scheme) {
        val t = scheme.tokens
        ButtonKind.entries.forEach { kind ->
            buttonColors(kind, enabled = false, tokens = t).second shouldBe t.content.dim
            buttonColors(kind, enabled = true, tokens = t).second shouldBe
                (if (kind == ButtonKind.PRIMARY) t.content.accentInk else t.content.text)
        }
    }

    companion object {
        @JvmStatic
        fun schemes(): Stream<Scheme> = Stream.of(Scheme("plex", SampleTokens.colors()), Scheme("paper", paperColors()))
    }
}
