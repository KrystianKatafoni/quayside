package dev.agentmanager.terminal.api

import dev.agentmanager.core.domain.Outcome
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Path

/** [FakeTerminalApi] sessions pass [TerminalSearchContract] (ENG-T2). */
class FakeTerminalSearchContractTest : TerminalSearchContract() {
    private val api = FakeTerminalApi()

    override suspend fun sessionShowing(lines: List<String>): TerminalSession {
        val session =
            api
                .openShell(Path.of(System.getProperty("java.io.tmpdir")))
                .shouldBeInstanceOf<Outcome.Ok<TerminalSession>>()
                .value
                .shouldBeInstanceOf<FakeTerminalSession>()
        lines.forEach(session::print)
        return session
    }

    override suspend fun endProcess(session: TerminalSession) {
        session.shouldBeInstanceOf<FakeTerminalSession>().finish(0)
    }

    override fun tearDown() {
        api.close()
    }
}
