package dev.quayside.appearance.api

import dev.quayside.core.designtokens.Rgba
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CustomSchemeTest {
    private val mine = SampleSchemes.mine()

    @Test
    fun `sample scheme is valid`() {
        mine.colors.all().size shouldBe 8
    }

    @Test
    fun `blank name is refused`() {
        shouldThrow<IllegalArgumentException> { mine.copy(name = " ") }
    }

    @Test
    fun `name of the longest size is accepted and one longer is refused`() {
        mine.copy(name = "x".repeat(CustomScheme.MAX_NAME))
        shouldThrow<IllegalArgumentException> { mine.copy(name = "x".repeat(CustomScheme.MAX_NAME + 1)) }
    }

    @Test
    fun `radius is limited to the range`() {
        mine.copy(radius = 0)
        mine.copy(radius = CustomScheme.MAX_RADIUS)
        shouldThrow<IllegalArgumentException> { mine.copy(radius = -1) }
        shouldThrow<IllegalArgumentException> { mine.copy(radius = CustomScheme.MAX_RADIUS + 1) }
    }

    @Test
    fun `translucent colour is refused`() {
        shouldThrow<IllegalArgumentException> { mine.colors.copy(panel = Rgba(0x80FFFFFFL)) }
    }
}
