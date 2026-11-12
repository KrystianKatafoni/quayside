package dev.quayside.core.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AccountIdTest {
    @ParameterizedTest
    @ValueSource(strings = ["a", "praca-2", "0", "default", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"])
    fun `a slug of 1 to 32 characters is accepted`(value: String) {
        AccountId(value).value shouldBe value
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "Praca", "a b", "a_b", "żółw", "a/b", "../x", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"])
    fun `anything else is a programmer error`(value: String) {
        shouldThrow<IllegalArgumentException> { AccountId(value) }
    }

    @Test
    fun `the default account is called default and prints as its slug`() {
        AccountId.DEFAULT.value shouldBe "default"
        AccountId("praca").toString() shouldBe "praca"
    }
}
