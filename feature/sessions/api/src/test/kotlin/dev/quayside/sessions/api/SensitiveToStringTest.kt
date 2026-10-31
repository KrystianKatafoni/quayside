package dev.quayside.sessions.api

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class SensitiveToStringTest {
    @Test
    fun `AwaitingInput hides the question`() {
        val text = SessionState.AwaitingInput(question = "secret question").toString()

        text shouldNotContain "secret"
        text shouldContain "***"
    }

    @Test
    fun `Session hides the summary`() {
        val text = aSession(summary = "secret summary").toString()

        text shouldNotContain "secret"
        text shouldContain "***"
        text shouldContain "7c5dcf5d"
    }

    @Test
    fun `Session hides the question of its state`() {
        aSession(state = SessionState.AwaitingInput("secret question")).toString() shouldNotContain "secret"
    }

    @Test
    fun `NewSessionSpec hides the prompt`() {
        val text = NewSessionSpec(prompt = "secret prompt", name = "fix-login").toString()

        text shouldNotContain "secret"
        text shouldContain "***"
        text shouldContain "fix-login"
    }

    @Test
    fun `recent output hides its text`() {
        val text = RecentOutput("secret output").toString()

        text shouldNotContain "secret"
        text shouldContain "***"
    }
}
