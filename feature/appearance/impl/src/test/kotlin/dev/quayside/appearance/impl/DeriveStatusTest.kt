package dev.quayside.appearance.impl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** One row of table 2 of the AM-805 plan. */
data class StatusCase(
    val dark: Boolean,
    val accent: String,
    val expected: List<String>,
) {
    override fun toString(): String = (if (dark) "dark " else "light ") + accent
}

/** Table 2 of the AM-805 plan: the first candidate triple without a hue conflict with the accent. */
class DeriveStatusTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `picks the first candidate triple without hue conflict`(case: StatusCase) {
        val triple = deriveStatus(case.dark, hex(case.accent))

        listOf(triple.warn, triple.ok, triple.err) shouldBe case.expected.map(::hex)
    }

    @Test
    fun `fails when no candidate triple fits a red accent`() {
        shouldThrow<IllegalStateException> { deriveStatus(dark = false, accent = hex("#FF0000")) }
        shouldThrow<IllegalStateException> { deriveStatus(dark = true, accent = hex("#FF0000")) }
    }

    companion object {
        private val LIGHT = listOf("#A16207", "#15803D", "#B91C1C")
        private val DARK = listOf("#F59E0B", "#4ADE80", "#F87171")

        @JvmStatic
        fun cases(): Stream<StatusCase> =
            Stream.of(
                StatusCase(false, "#3182CE", LIGHT),
                StatusCase(false, "#0D9488", LIGHT),
                StatusCase(false, "#008000", listOf("#A16207", "#4D7C0F", "#B91C1C")),
                StatusCase(false, "#059669", listOf("#A16207", "#4D7C0F", "#B91C1C")),
                StatusCase(false, "#D97757", listOf("#0F766E", "#15803D", "#BE185D")),
                StatusCase(true, "#3B82F6", DARK),
                StatusCase(true, "#FF79C6", DARK),
                StatusCase(true, "#FFFFFF", DARK),
                StatusCase(true, "#F92672", listOf("#FACC15", "#4ADE80", "#FF6E40")),
                StatusCase(true, "#FE8019", listOf("#5EEAD4", "#A3E635", "#FB7185")),
                StatusCase(true, "#00FFCC", listOf("#F59E0B", "#A3E635", "#F87171")),
                StatusCase(true, "#F59E0B", listOf("#5EEAD4", "#A3E635", "#F87171")),
            )
    }
}
