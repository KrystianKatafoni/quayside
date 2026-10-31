package dev.agentmanager.commandpalette.api

import kotlinx.coroutines.test.TestScope

/** [FakeCommandRegistry] passes [CommandRegistryContract] (ENG-T2). */
class FakeCommandRegistryContractTest : CommandRegistryContract() {
    override fun TestScope.create(): CommandRegistry = FakeCommandRegistry(backgroundScope)
}
