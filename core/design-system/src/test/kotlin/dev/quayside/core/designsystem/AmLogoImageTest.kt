package dev.quayside.core.designsystem

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** The Dock icon: the full drawing rasterised to an ARGB image (plan AM-822, `amLogoImage`). */
class AmLogoImageTest {
    @Test
    fun `image has the requested size and the full drawing`() {
        val image = amLogoImage(SIZE)

        image.width shouldBe SIZE
        image.height shouldBe SIZE
        fullPoints().forEach { point ->
            val x = unitToPixel(point.x, SIZE, VIEW_BOX)
            val y = unitToPixel(point.y, SIZE, VIEW_BOX)
            shouldBeNear(image.getRGB(x, y), point.expected.argb32(), clue = "${point.label} ($x, $y)")
        }
    }

    @Test
    fun `rounded corner of the image is transparent`() {
        val image = amLogoImage(SIZE)

        (image.getRGB(0, 0) ushr ALPHA_SHIFT) shouldBe 0
        (image.getRGB(SIZE / 2, SIZE / 2) ushr ALPHA_SHIFT) shouldBe OPAQUE
    }

    @Test
    fun `a small image still uses the full drawing`() {
        val image = amLogoImage(SMALL_PX)

        // unit (26; 8.67) lies in the main beam of the full drawing; the simplified drawing has no beam there
        val x = unitToPixel(26.0, SMALL_PX, VIEW_BOX)
        val y = unitToPixel(8.67, SMALL_PX, VIEW_BOX)
        shouldBeNear(image.getRGB(x, y), LogoColors.onTile(LogoColors.beam, 90).argb32(), clue = "beam ($x, $y)")
    }

    @ParameterizedTest(name = "{0} px")
    @ValueSource(ints = [0, -1])
    fun `non positive size throws`(sizePx: Int) {
        shouldThrow<IllegalArgumentException> { amLogoImage(sizePx) }
    }

    private companion object {
        const val SIZE = 256
        const val SMALL_PX = 24
        const val VIEW_BOX = 32
        const val ALPHA_SHIFT = 24
        const val OPAQUE = 255
    }
}
