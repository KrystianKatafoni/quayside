package dev.quayside.accounts.api

import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * What every [AccountSetup] must do as seen through [AccountsApi] (ENG-T2). Runs on [FakeAccounts] and on the
 * file implementation; the disk effects of the real one are tested in `accounts:impl`.
 */
public abstract class AccountSetupContract {
    @TempDir
    public lateinit var tempDir: Path

    /** The user's home directory of this test; `~/.claude` exists. */
    protected val home: Path get() = tempDir.resolve("home")

    /** An api over a user home [home] with nothing stored. */
    protected abstract fun create(home: Path): AccountsApi

    /** Makes the next save of the account list fail. */
    protected abstract fun breakRegistry(api: AccountsApi)

    @BeforeEach
    public fun defaultDirExists() {
        Files.createDirectories(home.resolve(".claude"))
    }

    private fun ids(api: AccountsApi): List<AccountId> =
        api.repo.state.value.accounts
            .map { it.id }

    @Test
    public fun `create registers the account last with the slug id`(): Unit =
        runTest {
            val api = create(home)
            api.setup.create(NewAccount("Prywatne"))

            val result = api.setup.create(NewAccount("Praca Zdalna"))

            val account = (result as Outcome.Ok).value
            account.id shouldBe AccountId("praca-zdalna")
            account.label shouldBe "Praca Zdalna"
            account.configDir shouldBe home.resolve(".claude-quayside").resolve("praca-zdalna")
            ids(api) shouldBe listOf(AccountId.DEFAULT, AccountId("prywatne"), AccountId("praca-zdalna"))
        }

    @Test
    public fun `create with a name giving an empty slug is InvalidName`(): Unit =
        runTest {
            val api = create(home)

            api.setup.create(NewAccount("!!!")) shouldBe Outcome.Err(AccountSetupError.InvalidName)

            ids(api) shouldBe listOf(AccountId.DEFAULT)
        }

    @Test
    public fun `create named default or auto is InvalidName`(): Unit =
        runTest {
            val api = create(home)

            api.setup.create(NewAccount("default")) shouldBe Outcome.Err(AccountSetupError.InvalidName)
            api.setup.create(NewAccount("Auto")) shouldBe Outcome.Err(AccountSetupError.InvalidName)
        }

    @Test
    public fun `second account with the same slug is AlreadyExists`(): Unit =
        runTest {
            val api = create(home)
            api.setup.create(NewAccount("Praca"))

            api.setup.create(NewAccount("praca!")) shouldBe Outcome.Err(AccountSetupError.AlreadyExists)

            ids(api).size shouldBe 2
        }

    @Test
    public fun `create after register of the same name is AlreadyExists and makes no directory`(): Unit =
        runTest {
            val api = create(home)
            api.setup.register("Praca", Files.createDirectories(home.resolve("work")))

            api.setup.create(NewAccount("Praca")) shouldBe Outcome.Err(AccountSetupError.AlreadyExists)

            Files.exists(home.resolve(".claude-quayside").resolve("praca")) shouldBe false
            ids(api) shouldBe listOf(AccountId.DEFAULT, AccountId("praca"))
        }

    @Test
    public fun `register of the default dir is DefaultDirectory`(): Unit =
        runTest {
            val api = create(home)

            api.setup.register("Domyslne", home.resolve(".claude")) shouldBe
                Outcome.Err(AccountSetupError.DefaultDirectory)
        }

    @Test
    public fun `register of a registered dir is AlreadyExists`(): Unit =
        runTest {
            val api = create(home)
            val dir = Files.createDirectories(home.resolve("work"))
            api.setup.register("Work", dir)

            api.setup.register("Inne", dir) shouldBe Outcome.Err(AccountSetupError.AlreadyExists)
        }

    @Test
    public fun `register of a dir registers it last`(): Unit =
        runTest {
            val api = create(home)
            val dir = Files.createDirectories(home.resolve("work"))

            val account = (api.setup.register("Work", dir) as Outcome.Ok).value

            account.id shouldBe AccountId("work")
            account.configDir shouldBe dir
            ids(api) shouldBe listOf(AccountId.DEFAULT, AccountId("work"))
        }

    @Test
    public fun `copy into the default account is DefaultDirectory`(): Unit =
        runTest {
            val api = create(home)

            api.setup.copyFromDefault(AccountId.DEFAULT, setOf(CopyItem.SKILLS)) shouldBe
                Outcome.Err(AccountSetupError.DefaultDirectory)
        }

    @Test
    public fun `copy into an unknown account is UnknownAccount`(): Unit =
        runTest {
            val api = create(home)

            api.setup.copyFromDefault(AccountId("nie-ma"), setOf(CopyItem.SKILLS)) shouldBe
                Outcome.Err(AccountSetupError.UnknownAccount)
        }

    @Test
    public fun `failed registry save on create leaves no account`(): Unit =
        runTest {
            val api = create(home)
            breakRegistry(api)

            api.setup.create(NewAccount("Praca")) shouldBe
                Outcome.Err(AccountSetupError.Registry(AccountsError.WriteFailed))

            ids(api) shouldBe listOf(AccountId.DEFAULT)
        }

    @Test
    public fun `failed registry save on register leaves no account`(): Unit =
        runTest {
            val api = create(home)
            val dir = Files.createDirectories(home.resolve("work"))
            breakRegistry(api)

            api.setup.register("Work", dir) shouldBe
                Outcome.Err(AccountSetupError.Registry(AccountsError.WriteFailed))

            ids(api) shouldBe listOf(AccountId.DEFAULT)
        }
}
