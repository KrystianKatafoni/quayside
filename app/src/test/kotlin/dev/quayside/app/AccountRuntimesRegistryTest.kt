@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.app

import dev.quayside.accounts.api.Account
import dev.quayside.accounts.api.AccountsState
import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.core.domain.AccountId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** [AccountRuntimes]: one runtime per account except the default one, replaced only by a new `configDir` (AM-1003). */
class AccountRuntimesRegistryTest {
    private val home = Path.of("/Users/test")
    private val default = Account(AccountId.DEFAULT, null, home.resolve(".claude"))
    private val work = account("work")
    private val personal = account("personal")
    private val built = mutableListOf<Pair<AccountId, Path>>()

    private fun account(
        slug: String,
        label: String = slug,
        dir: Path = home.resolve(".claude-quayside").resolve(slug),
    ) = Account(AccountId(slug), label, dir)

    private fun TestScope.registry(state: MutableStateFlow<AccountsState>): AccountRuntimes =
        AccountRuntimes(state, backgroundScope) { id, dir ->
            built += id to dir
            FakeAgentRuntime(RuntimeWorld(account = id))
        }

    private fun stateOf(vararg accounts: Account) = MutableStateFlow(AccountsState(accounts.toList()))

    private fun AccountRuntimes.runtimeOf(id: AccountId): AgentRuntime = runtimes.value.getValue(id)

    @Test
    fun `builds one runtime per non-default account in file order`() =
        runTest {
            val registry = registry(stateOf(default, personal, work))

            registry.runtimes.value.keys
                .toList() shouldBe listOf(personal.id, work.id)
            built shouldBe listOf(personal.id to personal.configDir, work.id to work.configDir)
        }

    @Test
    fun `builds nothing for the default account`() =
        runTest {
            val registry = registry(stateOf(default))

            registry.runtimes.value shouldBe emptyMap()
            built shouldBe emptyList()
        }

    @Test
    fun `a disabled account still has a runtime`() =
        runTest {
            val registry = registry(stateOf(default, work.copy(enabled = false, inAutomation = false)))

            registry.runtimes.value.keys
                .toList() shouldBe listOf(work.id)
        }

    @Test
    fun `a changed label keeps the runtime instance`() =
        runTest {
            val state = stateOf(default, work)
            val registry = registry(state)
            val before = registry.runtimeOf(work.id)

            state.value = AccountsState(listOf(default, work.copy(label = "Work, renamed")))
            runCurrent()

            registry.runtimeOf(work.id) shouldBeSameInstanceAs before
            built.size shouldBe 1
        }

    @Test
    fun `changed flags keep the runtime instance`() =
        runTest {
            val state = stateOf(default, work)
            val registry = registry(state)
            val before = registry.runtimeOf(work.id)

            state.value = AccountsState(listOf(default, work.copy(enabled = false, inAutomation = false)))
            runCurrent()

            registry.runtimeOf(work.id) shouldBeSameInstanceAs before
            built.size shouldBe 1
        }

    @Test
    fun `a changed configDir builds a new runtime and keeps the other ones`() =
        runTest {
            val state = stateOf(default, work, personal)
            val registry = registry(state)
            val oldWork = registry.runtimeOf(work.id)
            val oldPersonal = registry.runtimeOf(personal.id)
            val moved = home.resolve(".claude-quayside").resolve("work-moved")

            state.value = AccountsState(listOf(default, work.copy(configDir = moved), personal))
            runCurrent()

            (registry.runtimeOf(work.id) === oldWork) shouldBe false
            registry.runtimeOf(personal.id) shouldBeSameInstanceAs oldPersonal
            built.last() shouldBe (work.id to moved)
            built.size shouldBe 3
        }

    @Test
    fun `a new account gets a runtime while the others keep theirs`() =
        runTest {
            val state = stateOf(default, work)
            val registry = registry(state)
            val oldWork = registry.runtimeOf(work.id)

            state.value = AccountsState(listOf(default, work, personal))
            runCurrent()

            registry.runtimes.value.keys
                .toList() shouldBe listOf(work.id, personal.id)
            registry.runtimeOf(work.id) shouldBeSameInstanceAs oldWork
            built.map { it.first } shouldBe listOf(work.id, personal.id)
        }

    @Test
    fun `a removed account loses its runtime`() =
        runTest {
            val state = stateOf(default, work, personal)
            val registry = registry(state)

            state.value = AccountsState(listOf(default, personal))
            runCurrent()

            registry.runtimes.value.keys
                .toList() shouldBe listOf(personal.id)
        }

    @Test
    fun `reordering the file reorders the runtimes without building new ones`() =
        runTest {
            val state = stateOf(default, work, personal)
            val registry = registry(state)
            val oldWork = registry.runtimeOf(work.id)

            state.value = AccountsState(listOf(default, personal, work))
            runCurrent()

            registry.runtimes.value.keys
                .toList() shouldBe listOf(personal.id, work.id)
            registry.runtimeOf(work.id) shouldBeSameInstanceAs oldWork
            built.size shouldBe 2
        }
}
