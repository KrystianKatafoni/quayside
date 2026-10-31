package dev.quayside.commandpalette.impl

import dev.quayside.commandpalette.api.CommandRegistry
import dev.quayside.commandpalette.api.CommandRegistryContract
import dev.quayside.core.testing.RecordingLogger
import kotlinx.coroutines.test.TestScope

/** The registry of `commandPaletteModule` passes [CommandRegistryContract] (ENG-T2). */
class InMemoryCommandRegistryContractTest : CommandRegistryContract() {
    override fun TestScope.create(): CommandRegistry = InMemoryCommandRegistry(backgroundScope, RecordingLogger())
}
