package dev.quayside.accounts.impl

import dev.quayside.accounts.api.AccountSetupContract
import dev.quayside.accounts.api.AccountsApi
import dev.quayside.accounts.api.AccountsRepo
import dev.quayside.accounts.api.AccountsRepoContract
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import java.nio.file.Path
import java.util.concurrent.Executors

/** [AccountsRepoContract] on the real repository: `accounts.json` in a temp directory (ENG-T2). */
class JsonAccountsRepoContractTest : AccountsRepoContract() {
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val env by lazy { AccountsEnv(tempDir, io) }

    @AfterEach
    fun closeDispatcher() = io.close()

    // The contract's factory is not suspending; runBlocking is allowed in tests (ENG-C3).
    @Suppress("ForbiddenMethodCall")
    override fun create(home: Path): AccountsRepo = runBlocking { env.open().repo }

    override fun failNextSave(repo: AccountsRepo) = env.breakRegistry()
}

/** [AccountSetupContract] on the real implementation over a temp home. */
class FileAccountSetupContractTest : AccountSetupContract() {
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val env by lazy { AccountsEnv(tempDir, io) }

    @AfterEach
    fun closeDispatcher() = io.close()

    @Suppress("ForbiddenMethodCall")
    override fun create(home: Path): AccountsApi = runBlocking { env.open() }

    override fun breakRegistry(api: AccountsApi) = env.breakRegistry()
}
