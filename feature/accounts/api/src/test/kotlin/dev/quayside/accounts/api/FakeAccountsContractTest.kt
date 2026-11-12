package dev.quayside.accounts.api

import java.nio.file.Path

/** [AccountsRepoContract] on the in-memory fake. */
class FakeAccountsRepoContractTest : AccountsRepoContract() {
    override fun create(home: Path): AccountsRepo = FakeAccounts(home)

    override fun failNextSave(repo: AccountsRepo) = (repo as FakeAccounts).failNextSave()
}

/** [AccountSetupContract] on the in-memory fake. */
class FakeAccountSetupContractTest : AccountSetupContract() {
    override fun create(home: Path): AccountsApi = FakeAccounts(home).let { AccountsApi(it, it) }

    override fun breakRegistry(api: AccountsApi) = (api.repo as FakeAccounts).failNextSave()
}
