package dev.agentmanager.commandpalette.impl

import dev.agentmanager.commandpalette.api.CommandRegistry
import dev.agentmanager.commandpalette.api.CommandRegistryContract
import dev.agentmanager.core.testing.RecordingLogger
import kotlinx.coroutines.test.TestScope

/** The registry of `commandPaletteModule` passes [CommandRegistryContract] (ENG-T2). */
class InMemoryCommandRegistryContractTest : CommandRegistryContract() {
    override fun TestScope.create(): CommandRegistry = InMemoryCommandRegistry(backgroundScope, RecordingLogger())
}
