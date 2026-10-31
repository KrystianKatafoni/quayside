package dev.quayside.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QualityThresholdsTest {
    @Test
    fun `coverage targets follow the test strategy`() {
        assertEquals(80, QualityThresholds.lineCoverage(":feature:sessions:impl"))
        assertEquals(90, QualityThresholds.lineCoverage(":core:domain"))
        assertEquals(0, QualityThresholds.lineCoverage(":feature:sessions:api"))
        assertEquals(0, QualityThresholds.lineCoverage(":tests:architecture"))
    }
}
