@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.app

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.impl.agentRuntimeModule
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * [accountAttachCommand]: `claude attach` runs under the `CLAUDE_CONFIG_DIR` of the account of the session
 * (AM-1003). The runtimes are the real adapter over a runner that never starts a process.
 */
class SessionAttachTest {
    private val home = Path.of("/Users/test")
    private val workDir = home.resolve(".claude-quayside").resolve("work")
    private val work = AccountId("work")
    private val runner = FakeProcessRunner()

    private fun runtime(
        account: AccountId = AccountId.DEFAULT,
        dir: Path? = null,
    ): AgentRuntime = agentRuntimeModule(runner, TestClock(), NoOpLogger, home, account = account, configDir = dir)

    private val default = runtime()
    private val workRuntime = runtime(work, workDir)

    private fun snapshotOf(vararg sessions: Session) =
        MutableStateFlow(SessionsSnapshot(sessions.toList(), SnapshotStatus.Fresh(T0)))

    private val attach =
        accountAttachCommand(
            default,
            MutableStateFlow(mapOf(work to workRuntime)),
            snapshotOf(aSession(id = "w0000001", account = work), aSession(id = "d0000001")),
        )

    @Test
    fun `attach of a session on account work carries its CLAUDE_CONFIG_DIR`() {
        val command = attach(SessionId("w0000001"))

        command.argv shouldBe listOf("claude", "attach", "w0000001")
        command.env shouldBe mapOf("CLAUDE_CONFIG_DIR" to workDir.toString())
    }

    @Test
    fun `attach of a session on the default account has no CLAUDE_CONFIG_DIR`() {
        attach(SessionId("d0000001")).env shouldBe emptyMap()
    }

    @Test
    fun `attach of an unknown session uses the default runtime`() {
        val command = attach(SessionId("ffffffff"))

        command shouldBe default.attachCommand(SessionId("ffffffff"))
        command.env shouldBe emptyMap()
    }

    @Test
    fun `attach of a session whose account has no runtime uses the default runtime`() {
        val gone = accountAttachCommand(default, MutableStateFlow(emptyMap()), snapshotOf(aSession(account = work)))

        gone(SessionId("7c5dcf5d")) shouldBe default.attachCommand(SessionId("7c5dcf5d"))
    }

    @Test
    fun `attach follows the latest snapshot and the latest runtimes`() {
        val sessions = snapshotOf()
        val extra = MutableStateFlow<Map<AccountId, AgentRuntime>>(emptyMap())
        val command = accountAttachCommand(default, extra, sessions)
        command(SessionId("7c5dcf5d")).env shouldBe emptyMap()

        sessions.value = SessionsSnapshot(listOf(aSession(account = work)), SnapshotStatus.Fresh(T0))
        extra.value = mapOf(work to workRuntime)

        command(SessionId("7c5dcf5d")).env shouldBe mapOf("CLAUDE_CONFIG_DIR" to workDir.toString())
    }
}
