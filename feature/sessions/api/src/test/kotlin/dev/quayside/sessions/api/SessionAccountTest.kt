package dev.quayside.sessions.api

import dev.quayside.core.domain.AccountId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

/** The account of a session and of a start (AM-1003, ADR 0036). */
class SessionAccountTest {
    @Test
    fun `a session and a new session spec default to the default account`() {
        aSession().account shouldBe AccountId.DEFAULT
        pastSession().account shouldBe AccountId.DEFAULT
        NewSessionSpec(prompt = "go").account shouldBe AccountId.DEFAULT
    }

    @Test
    fun `toString of a session prints the account id`() {
        aSession(account = AccountId("work")).toString() shouldContain "account=work"
    }
}
