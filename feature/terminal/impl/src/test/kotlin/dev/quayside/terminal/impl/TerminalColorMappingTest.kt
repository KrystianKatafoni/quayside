package dev.quayside.terminal.impl

import com.jediterm.terminal.TerminalColor
import dev.quayside.core.designtokens.Rgba
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** `Rgba` to JediTerm colour (AM-807, table 1): channels keep their places, alpha is dropped. */
class TerminalColorMappingTest {
    @ParameterizedTest(name = "{0} maps to ({1}, {2}, {3})")
    @MethodSource("tokens")
    fun `maps red green and blue of the token`(
        argb: Long,
        red: Int,
        green: Int,
        blue: Int,
    ) {
        Rgba(argb).toTerminalColor() shouldBe TerminalColor.rgb(red, green, blue)
    }

    @ParameterizedTest(name = "alpha {0} is dropped")
    @MethodSource("alphas")
    fun `drops alpha`(alpha: Long) {
        val translucent = Rgba((alpha shl ALPHA_SHIFT) or RGB)
        val opaque = Rgba(0xFF00_0000L or RGB)

        translucent.toTerminalColor() shouldBe opaque.toTerminalColor()
    }

    companion object {
        private const val ALPHA_SHIFT = 24
        private const val RGB = 0x00E0D0C0L

        @JvmStatic
        fun tokens(): Stream<Arguments> =
            Stream.of(
                Arguments.of(0xFF000000L, 0, 0, 0),
                Arguments.of(0xFF102030L, 16, 32, 48),
                Arguments.of(0x80E0D0C0L, 224, 208, 192),
            )

        @JvmStatic
        fun alphas(): Stream<Long> = Stream.of(0x00L, 0x80L, 0xFEL)
    }
}
