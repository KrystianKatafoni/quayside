package dev.quayside.accounts.impl

import dev.quayside.accounts.api.defaultState
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The factory over a temp home. */
class AccountsModuleTest : AccountsFsTest() {
    @Test
    fun `accountsModule opens over a temp home with only the default account`() =
        runTest {
            val api = env.open()

            api.repo.state.value shouldBe defaultState(env.home)
            api.repo.state.value.accounts
                .single()
                .configDir shouldBe env.home.resolve(".claude")
        }
}
