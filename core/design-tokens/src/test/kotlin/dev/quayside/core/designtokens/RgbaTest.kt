package dev.quayside.core.designtokens

import dev.quayside.core.designtokens.SampleTokens.rgb
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RgbaTest {
    @ParameterizedTest(name = "parse accepts {0}")
    @ValueSource(strings = ["#A78BFA", "#a78bfa", "#A78bFa"])
    fun `parse accepts upper and lower case`(text: String) {
        val color = Rgba.parse(text)

        color shouldBe rgb(0xA78BFA)
        color?.isOpaque shouldBe true
    }

    @ParameterizedTest(name = "parse rejects \"{0}\"")
    @ValueSource(
        strings = ["A78BFA", "#A78BF", "#A78BFAA", "#A78BFG", "", "#", "red", "#12 456", "0xA78BFA", " #A78BFA"],
    )
    fun `parse rejects missing hash, short, long and non-hex text`(text: String) {
        Rgba.parse(text).shouldBeNull()
    }

    @Test
    fun `withAlpha 60 gives 0x99 alpha`() {
        val color = rgb(0x102030).withAlpha(60)

        color.alpha shouldBe 0x99
        color.isOpaque shouldBe false
        color.red shouldBe 0x10
        color.green shouldBe 0x20
        color.blue shouldBe 0x30
    }

    @Test
    fun `withAlpha 0 and 100 give transparent and opaque`() {
        rgb(0x102030).withAlpha(0).alpha shouldBe 0
        rgb(0x102030).withAlpha(100).alpha shouldBe 0xFF
    }

    @Test
    fun `channels are unpacked from argb`() {
        val color = Rgba(0x80FF7F01)

        color.alpha shouldBe 0x80
        color.red shouldBe 0xFF
        color.green shouldBe 0x7F
        color.blue shouldBe 0x01
    }

    @Test
    fun `black and white are opaque`() {
        Rgba.BLACK shouldBe rgb(0x000000)
        Rgba.WHITE shouldBe rgb(0xFFFFFF)
    }

    @Test
    fun `rejects values outside 0 to 0xFFFFFFFF`() {
        shouldThrow<IllegalArgumentException> { Rgba(-1) }
        shouldThrow<IllegalArgumentException> { Rgba(0x1_0000_0000) }
    }
}
