package dev.quayside.core.testing

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BuildersTest {
    @Test
    fun `builders provide valid defaults and accept overrides`() {
        aProjectId().value shouldBe "agent-manager"
        aSessionId("abc12345").value shouldBe "abc12345"
        aShellCommand().argv shouldBe listOf("claude", "agents", "--json")
        aShellCommand("git", "status").argv shouldBe listOf("git", "status")
    }
}
