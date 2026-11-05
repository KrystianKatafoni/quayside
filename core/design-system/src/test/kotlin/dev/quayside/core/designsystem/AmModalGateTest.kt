package dev.quayside.core.designsystem

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AmModalGateTest {
    @Test
    fun `is active while a layer is shown and nested layers keep it active`() {
        val gate = AmModalGate()
        gate.active shouldBe false

        gate.enter()
        gate.enter()
        gate.leave()
        gate.active shouldBe true

        gate.leave()
        gate.active shouldBe false
    }

    @Test
    fun `an extra leave does not make the next layer count wrong`() {
        val gate = AmModalGate()
        gate.leave()
        gate.enter()
        gate.active shouldBe true
    }
}
