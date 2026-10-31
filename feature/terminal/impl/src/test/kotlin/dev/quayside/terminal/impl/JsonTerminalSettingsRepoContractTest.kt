package dev.quayside.terminal.impl

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.storage.InMemoryJsonStore
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsRepo
import dev.quayside.terminal.api.TerminalSettingsRepoContract

/** [JsonTerminalSettingsRepo] over an [InMemoryJsonStore] passes [TerminalSettingsRepoContract] (ENG-T2). */
class JsonTerminalSettingsRepoContractTest : TerminalSettingsRepoContract() {
    override suspend fun create(): TerminalSettingsRepo =
        JsonTerminalSettingsRepo.open(InMemoryJsonStore(TerminalSettings()), NoOpLogger)
}
