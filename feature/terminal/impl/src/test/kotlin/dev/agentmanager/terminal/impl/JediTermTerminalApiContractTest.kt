package dev.agentmanager.terminal.impl

import dev.agentmanager.core.domain.NoOpLogger
import dev.agentmanager.terminal.api.TerminalApi
import dev.agentmanager.terminal.api.TerminalApiContract
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Timeout
import java.nio.file.Path

/** The real `terminalModule` (pty4j + JediTerm, `/bin/sh`) passes [TerminalApiContract]; needs a display. */
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class JediTermTerminalApiContractTest : TerminalApiContract() {
    private val kits = mutableListOf<TerminalTestKit>()

    override fun create(dir: Path): TerminalApi {
        assumeFalse(TerminalTestKit.headless, "JediTermWidget needs a display")
        return TerminalTestKit(dir, NoOpLogger).also { kits += it }.api
    }

    override fun tearDown() {
        kits.forEach { it.close() }
    }
}
