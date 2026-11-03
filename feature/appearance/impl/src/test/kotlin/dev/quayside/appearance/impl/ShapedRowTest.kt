package dev.quayside.appearance.impl

import dev.quayside.appearance.api.ThemeMode
import dev.quayside.appearance.impl.ShapedRow.Column
import dev.quayside.core.designtokens.Backdrop
import dev.quayside.core.designtokens.Rgba
import dev.quayside.core.designtokens.ShadowSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ShapedRowTest {
    private fun row(
        colors: String = COLORS,
        shape: String = "1 8 none",
    ): ShapedRow = ShapedRow("t", "T", ThemeMode.LIGHT, colors, shape, "plex")

    @Test
    fun `parses colour with alpha percent`() {
        val palette = row("#F3F0FF #FFFFFF/60 #3B2A5C #796696 #8B5CF6 #123456/20").palette

        palette.getValue(Column.BG) shouldBe hex("#F3F0FF")
        palette.getValue(Column.PANEL) shouldBe Rgba(0x99FFFFFFL)
        palette.getValue(Column.TEXT) shouldBe hex("#3B2A5C")
        palette.getValue(Column.MUTED) shouldBe hex("#796696")
        palette.getValue(Column.ACCENT) shouldBe hex("#8B5CF6")
        palette.getValue(Column.BORDER) shouldBe Rgba(0x33123456L)
    }

    @Test
    fun `alpha percent 100 is opaque and 0 is transparent`() {
        val palette = row("#F3F0FF #FFFFFF/100 #3B2A5C #796696 #8B5CF6 #123456/0").palette

        palette.getValue(Column.PANEL) shouldBe hex("#FFFFFF")
        palette.getValue(Column.BORDER) shouldBe Rgba(0x00123456L)
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(
        strings = [
            "#F3F0FF #FFFFFF #3B2A5C #796696 #8B5CF6",
            "#F3F0FF #FFFFFF #3B2A5C #796696 #8B5CF6 #123456 #654321",
            "#F3F0FF #FFFFFF #3B2A5C #796696 #8B5CF6 #12345",
            "#F3F0FF #FFFFFF/101 #3B2A5C #796696 #8B5CF6 #123456",
            "#F3F0FF #FFFFFF/-1 #3B2A5C #796696 #8B5CF6 #123456",
            "#F3F0FF #FFFFFF/ #3B2A5C #796696 #8B5CF6 #123456",
            "#F3F0FF #FFFFFF/6x #3B2A5C #796696 #8B5CF6 #123456",
            "#F3F0FF #FFFFFF #3B2A5C #796696 #8B5CF6 #123456/",
            "",
        ],
    )
    fun `rejects wrong colour count or bad alpha`(colors: String) {
        shouldThrow<IllegalArgumentException> { row(colors = colors) }
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(
        strings = [
            "1 8",
            "x 8 none",
            "1 y none",
            "1 8 none extra",
            "1 8 4/6/-1/#000000",
            "1 8 4/6/-1/#000000/101",
            "1 8 4/6/-1/#00000/5",
            "1 8 4/6/#000000/5",
            "1 8 4/-6/0/#000000/5",
            "1 -8 none",
            "-1 8 none",
        ],
    )
    fun `rejects a malformed shape`(shape: String) {
        shouldThrow<IllegalArgumentException> { row(shape = shape) }
    }

    @Test
    fun `parses shape with shadow`() {
        val tokens = row(shape = "2 12 10/15/-3/#000000/2").shapeTokens

        tokens.borderWidth shouldBe 2
        tokens.radius shouldBe 12
        tokens.shadow shouldBe ShadowSpec(offsetY = 10, blur = 15, spread = -3, color = Rgba.BLACK.withAlpha(2))
    }

    @Test
    fun `parses shape with a coloured shadow`() {
        val tokens = row(shape = "1 16 8/32/0/#8B5CF6/10").shapeTokens

        tokens.borderWidth shouldBe 1
        tokens.radius shouldBe 16
        tokens.shadow shouldBe ShadowSpec(offsetY = 8, blur = 32, spread = 0, color = hex("#8B5CF6").withAlpha(10))
    }

    @Test
    fun `parses shape without shadow`() {
        val tokens = row(shape = "1 4 none").shapeTokens

        tokens.borderWidth shouldBe 1
        tokens.radius shouldBe 4
        tokens.shadow.shouldBeNull()
        tokens.backdrop shouldBe Backdrop.OPAQUE
    }

    @Test
    fun `translucent panel gives TRANSLUCENT backdrop`() {
        row("#F3F0FF #FFFFFF/60 #3B2A5C #796696 #8B5CF6 #8B5CF6").shapeTokens.backdrop shouldBe Backdrop.TRANSLUCENT
    }

    @Test
    fun `opaque panel gives OPAQUE backdrop even when the border is translucent`() {
        row("#F3F0FF #FFFFFF #3B2A5C #796696 #8B5CF6 #8B5CF6/20").shapeTokens.backdrop shouldBe Backdrop.OPAQUE
        row("#F3F0FF #FFFFFF/100 #3B2A5C #796696 #8B5CF6 #8B5CF6/20").shapeTokens.backdrop shouldBe Backdrop.OPAQUE
    }

    private companion object {
        const val COLORS = "#F3F0FF #FFFFFF #3B2A5C #796696 #8B5CF6 #123456"
    }
}
