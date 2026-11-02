package dev.quayside.agentruntime.api

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration.Companion.seconds

class PromptRequestTest {
    private fun request(prompt: String) = PromptRequest(prompt, "haiku", 30.seconds)

    @Test
    fun `toString hides the prompt`() {
        val text = request("Summarize the confidential merger plan").toString()

        text shouldNotContain "merger"
        text shouldNotContain "Summarize"
        text shouldContain "haiku"
        text shouldContain "30s"
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\n\t "])
    fun `rejects a blank prompt`(prompt: String) {
        shouldThrow<IllegalArgumentException> { request(prompt) }
    }

    @Test
    fun `rejects a prompt longer than MAX_PROMPT and accepts exactly MAX_PROMPT`() {
        PromptRequest.MAX_PROMPT shouldBe 16_000

        shouldNotThrowAny { request("a".repeat(PromptRequest.MAX_PROMPT)) }
        shouldThrow<IllegalArgumentException> { request("a".repeat(PromptRequest.MAX_PROMPT + 1)) }
    }
}
