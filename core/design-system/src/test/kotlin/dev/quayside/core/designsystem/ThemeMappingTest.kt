package dev.quayside.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quayside.core.designtokens.Backdrop
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.ShadowSpec
import dev.quayside.core.designtokens.ShapeTokens
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** Table 1 of the AM-802 plan: tokens to Compose values. */
class ThemeMappingTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("colours")
    fun `rgba maps to the compose colour with alpha kept`(
        argb: Long,
        alpha: Int,
        red: Int,
        green: Int,
        blue: Int,
    ) {
        Rgba(argb).toColor() shouldBe Color(red = red, green = green, blue = blue, alpha = alpha)
    }

    @Test
    fun `black maps to compose black`() {
        Rgba.BLACK.toColor() shouldBe Color.Black
    }

    @Test
    fun `translucent white keeps alpha 0_6 and full channels`() {
        val colour = Rgba(0x99FFFFFF).toColor()

        colour.alpha shouldBe (0x99 / 255f)
        listOf(colour.red, colour.green, colour.blue) shouldBe listOf(1f, 1f, 1f)
    }

    @Test
    fun `opaque shape maps to rounded corner border and no shadow`() {
        val shape = ShapeTokens(8, 1, null, Backdrop.OPAQUE).toAmShape()

        shape shouldBe AmShape(RoundedCornerShape(8.dp), 1.dp, null, Backdrop.OPAQUE)
    }

    @Test
    fun `square translucent shape keeps the shadow spec and the backdrop`() {
        val shadow = ShadowSpec(4, 6, -1, Rgba(0x33000000))

        val shape = ShapeTokens(0, 2, shadow, Backdrop.TRANSLUCENT).toAmShape()

        shape shouldBe AmShape(RoundedCornerShape(0.dp), 2.dp, shadow, Backdrop.TRANSLUCENT)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sizes")
    fun `text role has the planned font size`(
        role: TextRole,
        expectedSp: Int,
    ) {
        AmTypography(FontFamily.Serif, FontFamily.Cursive).style(role).fontSize shouldBe expectedSp.sp
    }

    companion object {
        @JvmStatic
        fun colours(): Stream<Arguments> =
            Stream.of(
                Arguments.of(0xFF0B0A10L, 0xFF, 0x0B, 0x0A, 0x10),
                Arguments.of(0xFF123456L, 0xFF, 0x12, 0x34, 0x56),
                Arguments.of(0x80AB12CDL, 0x80, 0xAB, 0x12, 0xCD),
                Arguments.of(0x00FFFFFFL, 0x00, 0xFF, 0xFF, 0xFF),
            )

        @JvmStatic
        fun sizes(): Stream<Arguments> =
            Stream.of(
                Arguments.of(TextRole.TITLE, 20),
                Arguments.of(TextRole.HEADING, 15),
                Arguments.of(TextRole.BODY, 13),
                Arguments.of(TextRole.CAPTION, 11),
                Arguments.of(TextRole.CODE, 13),
            )
    }
}
