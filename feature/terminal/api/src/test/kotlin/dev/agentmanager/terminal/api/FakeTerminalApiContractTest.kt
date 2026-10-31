package dev.agentmanager.terminal.api

import java.nio.file.Path

/** [FakeTerminalApi] passes [TerminalApiContract] (ENG-T2). */
class FakeTerminalApiContractTest : TerminalApiContract() {
    override fun create(dir: Path): TerminalApi = FakeTerminalApi()
}
