package dev.quayside.core.designtokens

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** The 16 ANSI colours of a terminal (AM-811, ADR 0027). */
class AnsiColorsTest {
    private fun distinct(count: Int = AnsiColors.SIZE): List<Rgba> =
        (0 until count).map { SampleTokens.rgb((it * 0x101010 + it).toLong()) }

    @ParameterizedTest(name = "{0} colours")
    @ValueSource(ints = [0, 1, 15])
    fun `rejects fewer than 16 colours`(count: Int) {
        shouldThrow<IllegalArgumentException> { AnsiColors(distinct(count)) }
    }

    @ParameterizedTest(name = "{0} colours")
    @ValueSource(ints = [17, 32])
    fun `rejects more than 16 colours`(count: Int) {
        shouldThrow<IllegalArgumentException> { AnsiColors(distinct(count)) }
    }

    @Test
    fun `get returns the colour of the xterm index`() {
        val source = distinct()
        val ansi = AnsiColors(source)

        (0 until AnsiColors.SIZE).forEach { ansi[it] shouldBe source[it] }
        source.toSet().size shouldBe AnsiColors.SIZE
    }

    @Test
    fun `get rejects an index outside 0 to 15`() {
        val ansi = AnsiColors(distinct())

        shouldThrow<IndexOutOfBoundsException> { ansi[-1] }
        shouldThrow<IndexOutOfBoundsException> { ansi[AnsiColors.SIZE] }
    }

    @Test
    fun `palette does not change when the source list changes`() {
        val source = distinct().toMutableList()
        val ansi = AnsiColors(source)
        val first = source[0]

        source[0] = SampleTokens.rgb(0xABCDEF)

        ansi[0] shouldBe first
        ansi.colors[0] shouldBe first
    }

    @Test
    fun `palettes with equal colours are equal`() {
        val a = AnsiColors(distinct())
        val b = AnsiColors(distinct())

        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
        a.toString() shouldBe b.toString()
    }

    @Test
    fun `palettes differing in one colour are not equal`() {
        val changed = distinct().toMutableList().also { it[7] = SampleTokens.rgb(0xABCDEF) }

        AnsiColors(distinct()) shouldNotBe AnsiColors(changed)
    }
}
