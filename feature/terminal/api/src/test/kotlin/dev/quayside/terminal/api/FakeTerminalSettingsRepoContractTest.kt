package dev.quayside.terminal.api

/** [FakeTerminalSettingsRepo] passes [TerminalSettingsRepoContract] (ENG-T2). */
class FakeTerminalSettingsRepoContractTest : TerminalSettingsRepoContract() {
    override suspend fun create(): TerminalSettingsRepo = FakeTerminalSettingsRepo()
}
