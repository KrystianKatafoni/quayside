package dev.quayside.core.designtokens

import dev.quayside.core.designtokens.SampleTokens.rgb
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** The audit is test code shared with the theme catalog tests, so it has tests of its own. */
class ContrastAuditTest {
    private val base = SampleTokens.colors()

    private fun labels(colors: ColorTokens): List<String> =
        ContrastAudit.problems(colors).map { it.substringBefore(' ') }

    @Test
    fun `passing scheme has no problems`() {
        ContrastAudit.problems(base).shouldBeEmpty()
    }

    @Test
    fun `muted below 4_5 on panel is reported`() {
        val colors =
            base.copy(
                surfaces = base.surfaces.copy(bg = rgb(0x0B0A10), panel = rgb(0x3A3A4A)),
                content = base.content.copy(muted = rgb(0x7C7C8C)),
            )

        labels(colors) shouldBe listOf("muted/panel")
    }

    @Test
    fun `translucent panel is composited on bg`() {
        // On the white bg a half-transparent white panel is white; composited on black it would be grey 808080,
        // where the dark text and muted colours fall below 4.5 to 1.
        val colors = lightScheme().let { it.copy(surfaces = it.surfaces.copy(panel = rgb(0xFFFFFF).withAlpha(50))) }

        ContrastAudit.problems(lightScheme()).shouldBeEmpty()
        ContrastAudit.problems(colors).shouldBeEmpty()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleChanges")
    fun `one changed colour gives exactly the expected labels`(
        name: String,
        change: (ColorTokens) -> ColorTokens,
        expected: List<String>,
    ) {
        withClue(name) { labels(change(base)) shouldBe expected }
    }

    @Test
    fun `status below 3 to 1 on bg is reported`() {
        val colors = base.copy(status = base.status.copy(err = rgb(0x3B1F26)))

        labels(colors) shouldBe listOf("err/bg", "err/panel")
    }

    @Test
    fun `warn closer than 30 degrees to err is reported`() {
        val colors = base.copy(status = base.status.copy(err = rgb(0xFF8A3D)))

        labels(colors) shouldBe listOf("warn~err")
    }

    @Test
    fun `ok closer than 30 degrees to accent is reported`() {
        val colors = base.copy(status = base.status.copy(ok = rgb(0x9B8BFA)))

        labels(colors) shouldBe listOf("ok~accent")
    }

    @Test
    fun `grey accent has no hue conflict`() {
        val colors = base.copy(content = base.content.copy(accent = rgb(0xFFFFFF)))

        ContrastAudit.problems(colors).shouldBeEmpty()
    }

    private companion object {
        fun lightScheme(): ColorTokens =
            ColorTokens(
                surfaces =
                    SurfaceColors(
                        bg = rgb(0xFFFFFF),
                        panel = rgb(0xFFFFFF),
                        raised = rgb(0xFFFFFF),
                        sel = rgb(0xE0E0E0),
                        border = rgb(0xCCCCCC),
                        border2 = rgb(0xAAAAAA),
                    ),
                content =
                    ContentColors(
                        text = rgb(0x202020),
                        muted = rgb(0x505050),
                        dim = rgb(0x707070),
                        accent = rgb(0x6D28D9),
                        accentInk = Rgba.WHITE,
                    ),
                status =
                    StatusColors(
                        warn = rgb(0xA16207),
                        warnInk = Rgba.WHITE,
                        warnBg = rgb(0xFFF3D6),
                        ok = rgb(0x15803D),
                        err = rgb(0xB91C1C),
                        work = rgb(0x4338CA),
                    ),
                terminal = TerminalColors(termBg = rgb(0x1E242C), termText = rgb(0xE2E8F0)),
            )

        private fun case(
            name: String,
            expected: List<String>,
            change: (ColorTokens) -> ColorTokens,
        ): Arguments = Arguments.of(name, change, expected)

        @JvmStatic
        @Suppress("LongMethod")
        fun singleChanges(): Stream<Arguments> =
            Stream.of(
                case("text/bg", listOf("text/bg", "err/bg", "work/bg")) {
                    it.copy(
                        surfaces = it.surfaces.copy(bg = rgb(0x6A6A6A)),
                        content = it.content.copy(muted = Rgba.WHITE),
                    )
                },
                case("text/panel", listOf("text/panel", "err/panel", "work/panel")) {
                    it.copy(
                        surfaces = it.surfaces.copy(panel = rgb(0x6A6A6A)),
                        content = it.content.copy(muted = Rgba.WHITE),
                    )
                },
                case("muted/bg", listOf("muted/bg")) { it.copy(surfaces = it.surfaces.copy(bg = rgb(0x5A5A66))) },
                case("muted/panel", listOf("muted/panel")) {
                    it.copy(surfaces = it.surfaces.copy(panel = rgb(0x5A5A66)))
                },
                case("text/raised", listOf("text/raised")) {
                    it.copy(surfaces = it.surfaces.copy(raised = rgb(0xE0E0E8)))
                },
                case("text/sel", listOf("text/sel")) { it.copy(surfaces = it.surfaces.copy(sel = rgb(0xE0E0E8))) },
                case("termText/termBg", listOf("termText/termBg")) {
                    it.copy(terminal = it.terminal.copy(termBg = rgb(0xE0E0E8)))
                },
                case("accentInk/accent", listOf("accentInk/accent")) {
                    it.copy(content = it.content.copy(accent = rgb(0x303030)))
                },
                case("warnInk/warn", listOf("warnInk/warn")) { it.copy(status = it.status.copy(warnInk = Rgba.WHITE)) },
                case("warn/bg and warn/panel", listOf("warnInk/warn", "warn/bg", "warn/panel")) {
                    it.copy(status = it.status.copy(warn = rgb(0x2A2A2A)))
                },
                case("ok/bg and ok/panel", listOf("ok/bg", "ok/panel")) {
                    it.copy(status = it.status.copy(ok = rgb(0x1F3B26)))
                },
                case("err/bg and err/panel", listOf("err/bg", "err/panel")) {
                    it.copy(status = it.status.copy(err = rgb(0x3B1F26)))
                },
                case("work/bg and work/panel", listOf("work/bg", "work/panel")) {
                    it.copy(status = it.status.copy(work = rgb(0x1F1F3B)))
                },
                case("warn~accent", listOf("warn~accent")) {
                    it.copy(content = it.content.copy(accent = rgb(0xFBA524)))
                },
                case("warn~ok", listOf("warn~ok")) { it.copy(status = it.status.copy(ok = rgb(0xEFE086))) },
                case("warn~err", listOf("warn~err")) { it.copy(status = it.status.copy(err = rgb(0xFF8A3D))) },
                case("ok~accent", listOf("ok~accent")) { it.copy(status = it.status.copy(ok = rgb(0x9B8BFA))) },
                case("err~accent", listOf("err~accent")) {
                    it.copy(content = it.content.copy(accent = rgb(0xFCA5C0)))
                },
            )
    }
}
