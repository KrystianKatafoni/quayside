package dev.quayside.core.designtokens

import dev.quayside.core.designtokens.SampleTokens.rgb
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ShapeTokensTest {
    @Test
    fun `rejects negative radius`() {
        shouldThrow<IllegalArgumentException> { ShapeTokens(-1, 1, null, Backdrop.OPAQUE) }
    }

    @Test
    fun `rejects negative border width`() {
        shouldThrow<IllegalArgumentException> { ShapeTokens(8, -1, null, Backdrop.OPAQUE) }
    }

    @Test
    fun `zero radius and border width are allowed`() {
        ShapeTokens(0, 0, null, Backdrop.TRANSLUCENT).radius shouldBe 0
    }

    @Test
    fun `shadow allows negative spread but not negative blur`() {
        ShadowSpec(offsetY = 10, blur = 15, spread = -3, color = rgb(0x000000)).spread shouldBe -3

        shouldThrow<IllegalArgumentException> {
            ShadowSpec(offsetY = 10, blur = -1, spread = 0, color = rgb(0x000000))
        }
    }
}
