package dev.quayside.core.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class IdsTest {
    @Test
    fun `ids compare by value and print the raw value`() {
        ProjectId("agent-manager") shouldBe ProjectId("agent-manager")
        SessionId("7c5dcf5d").toString() shouldBe "7c5dcf5d"
        ProjectId("agent-manager").toString() shouldBe "agent-manager"
    }

    @Test
    fun `blank ids are rejected`() {
        shouldThrow<IllegalArgumentException> { ProjectId(" ") }
        shouldThrow<IllegalArgumentException> { SessionId("") }
    }
}
