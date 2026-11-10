package dev.quayside.agentruntime.api

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/** ENG-C8: the title is the first user message, so no string template may print it. */
class PastConversationTest {
    @Test
    fun `toString hides the title but keeps the other fields`() {
        val text = aPastConversation(title = "secret plan for the merger").toString()

        text shouldNotContain "secret plan"
        text shouldContain A_CONVERSATION_UUID
        text shouldContain "title="
    }
}
