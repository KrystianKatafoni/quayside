package dev.quayside.accounts.impl

import dev.quayside.accounts.api.AccountProblem
import dev.quayside.accounts.api.AccountsError
import dev.quayside.accounts.api.AccountsState
import dev.quayside.accounts.api.anAccount
import dev.quayside.accounts.api.defaultState
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files

/** Start-up, refused changes and failed saves of the repository on the real `JsonStorage`. */
class JsonAccountsRepoTest : AccountsFsTest() {
    private fun add(slug: String): (AccountsState) -> AccountsState =
        { it.copy(accounts = it.accounts + anAccount(slug, env.home)) }

    @Test
    fun `opening without a file creates no file`() =
        runTest {
            env.open()

            Files.exists(env.file) shouldBe false
            Files.exists(env.support) shouldBe false
        }

    @Test
    fun `an update is saved and found by the next instance`() =
        runTest {
            env.open().repo.update(add("praca"))

            val reopened =
                env
                    .open()
                    .repo.state.value

            reopened.accounts.map { it.id.value } shouldBe listOf("default", "praca")
            Files.exists(env.file) shouldBe true
        }

    @Test
    fun `an invalid change is refused, not saved and not published`() =
        runTest {
            val repo = env.open().repo

            val result = repo.update { it.copy(accounts = emptyList()) }

            result shouldBe Outcome.Err(AccountsError.Invalid(AccountProblem.DEFAULT_MISSING))
            Files.exists(env.file) shouldBe false
            repo.state.value shouldBe defaultState(env.home)
        }

    @Test
    fun `a failed save publishes nothing and the next update works again`() =
        runTest {
            val repo = env.open().repo
            env.breakRegistry()

            repo.update(add("praca")) shouldBe Outcome.Err(AccountsError.WriteFailed)
            repo.state.value shouldBe defaultState(env.home)

            env.file.toFile().deleteRecursively()
            repo.update(add("praca")).let { it is Outcome.Ok } shouldBe true
            repo.state.value.accounts.size shouldBe 2
        }

    @Test
    fun `a failed save logs a category and leaves no temp file`() =
        runTest {
            val repo = env.open().repo
            env.breakRegistry()

            repo.update(add("praca"))

            env.logger.entries
                .joinToString { it.message + it.fields }
                .shouldNotContain(env.home.toString())
            env.names(env.support) shouldBe setOf("accounts.json")
        }

    @Test
    fun `newer schema refuses every update and writes nothing`() =
        runTest {
            env.writeFile("""{"accounts": []}""", version = 2)
            val before = env.read(env.file)
            val repo = env.open().repo

            repo.update(add("praca")) shouldBe Outcome.Err(AccountsError.NewerSchema)
            repo.update { it } shouldBe Outcome.Err(AccountsError.NewerSchema)

            repo.state.value shouldBe defaultState(env.home)
            env.read(env.file) shouldBe before
            env.names(env.support) shouldBe setOf("accounts.json")
        }

    @Test
    fun `recovered file is logged by category only`() =
        runTest {
            env.writeFile("""{"accounts": [{"id": "SEKRET", "configDir": "/Users/sekret-user"}]}""")

            env.open()

            val warnings = env.logger.entriesAt(LogLevel.WARN)
            warnings.shouldNotBeEmpty()
            val text = env.logger.entries.joinToString { it.message + it.fields + it.cause?.message }
            text shouldNotContain "SEKRET"
            text shouldNotContain "sekret-user"
            text shouldNotContain env.home.toString()
        }

    @Test
    fun `a recovered file leaves the repository usable`() =
        runTest {
            env.writeFile("""{"cooldownMinutes": 5}""")
            val repo = env.open().repo

            repo.update(add("praca")).let { it is Outcome.Ok } shouldBe true

            Files.exists(env.support.resolve("accounts.json.broken")) shouldBe true
        }
}
