package dev.quayside.accounts.api

import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

/**
 * Behaviour every [AccountsRepo] must have (ENG-T2). Runs on [FakeAccounts] and on the file repository.
 * Persistence across instances is a property of the file repository and is tested there.
 */
public abstract class AccountsRepoContract {
    @TempDir
    public lateinit var tempDir: Path

    /** The user's home directory of this test. */
    protected val home: Path get() = tempDir.resolve("home")

    /** A repository with nothing stored, for the user home [home]. */
    protected abstract fun create(home: Path): AccountsRepo

    /** Makes the next save of [repo] fail; later saves may keep failing. */
    protected abstract fun failNextSave(repo: AccountsRepo)

    private fun add(vararg slugs: String): (AccountsState) -> AccountsState =
        { s -> s.copy(accounts = s.accounts + slugs.map { anAccount(it, home) }) }

    @Test
    public fun `nothing stored gives only the default account`(): Unit =
        runTest {
            create(home).state.value shouldBe defaultState(home)
        }

    @Test
    public fun `update publishes and returns the new state`(): Unit =
        runTest {
            val repo = create(home)

            val result = repo.update(add("praca"))

            val expected = AccountsState(listOf(defaultAccount(home), anAccount("praca", home)))
            result shouldBe Outcome.Ok(expected)
            repo.state.value shouldBe expected
        }

    @Test
    public fun `removing the default account is DEFAULT_MISSING and changes nothing`(): Unit =
        runTest {
            val repo = create(home)
            repo.update(add("praca"))
            val before = repo.state.value

            val result = repo.update { it.copy(accounts = it.accounts.filterNot(Account::isDefault)) }

            result shouldBe Outcome.Err(AccountsError.Invalid(AccountProblem.DEFAULT_MISSING))
            repo.state.value shouldBe before
        }

    @Test
    public fun `changing the default dir is DEFAULT_DIR_CHANGED`(): Unit =
        runTest {
            val repo = create(home)

            val result =
                repo.update { s ->
                    s.copy(accounts = s.accounts.map { it.copy(configDir = home.resolve("elsewhere")) })
                }

            result shouldBe Outcome.Err(AccountsError.Invalid(AccountProblem.DEFAULT_DIR_CHANGED))
            repo.state.value shouldBe defaultState(home)
        }

    @Test
    public fun `a duplicate id is refused`(): Unit =
        runTest {
            val repo = create(home)
            repo.update(add("praca"))

            val result = repo.update(add("praca"))

            result shouldBe Outcome.Err(AccountsError.Invalid(AccountProblem.DUPLICATE_ID))
        }

    @Test
    public fun `the reserved id auto is RESERVED_ID and changes nothing`(): Unit =
        runTest {
            val repo = create(home)

            val result = repo.update(add("auto"))

            result shouldBe Outcome.Err(AccountsError.Invalid(AccountProblem.RESERVED_ID))
            repo.state.value shouldBe defaultState(home)
        }

    @Test
    public fun `reorder keeps every account`(): Unit =
        runTest {
            val repo = create(home)
            repo.update(add("praca", "prywatne"))

            repo.update { s -> s.copy(accounts = listOf(s.accounts[2], s.accounts[0], s.accounts[1])) }

            repo.state.value.accounts
                .map { it.id } shouldBe
                listOf(AccountId("prywatne"), AccountId.DEFAULT, AccountId("praca"))
        }

    @Test
    public fun `a change that changes nothing is ok`(): Unit =
        runTest {
            val repo = create(home)

            repo.update { it } shouldBe Outcome.Ok(defaultState(home))

            repo.state.value shouldBe defaultState(home)
        }

    @Test
    public fun `a failed save is WriteFailed and publishes nothing`(): Unit =
        runTest {
            val repo = create(home)
            failNextSave(repo)

            repo.update(add("praca")) shouldBe Outcome.Err(AccountsError.WriteFailed)

            repo.state.value shouldBe defaultState(home)
        }

    @Test
    public fun `the cooldown can be changed within the range`(): Unit =
        runTest {
            val repo = create(home)
            val two = 2.hours

            repo.update { it.copy(cooldown = two) }

            repo.state.value.cooldown shouldBe two
        }

    @Test
    public fun `concurrent updates lose no account`(): Unit =
        runTest {
            val repo = create(home)

            List(CONCURRENT) { n -> async { repo.update(add("konto-$n")) } }.awaitAll()

            repo.state.value.accounts.size shouldBe CONCURRENT + 1
        }

    private companion object {
        const val CONCURRENT = 10
    }
}
