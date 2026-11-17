package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.AgentRuntime
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.ResumeSpec
import dev.quayside.agentruntime.api.RuntimeRequirements
import dev.quayside.agentruntime.api.aStartSpec
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.TestClock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The environment of each command of [agentRuntimeModule] for an account, on a scripted runner: `CLAUDE_CONFIG_DIR`
 * together with `AM_HOOK_TOKEN` (plan AM-1003). The same on real processes: [ClaudeAccountIntegrationTest].
 */
class ClaudeAccountEnvTest {
    private val home = Path.of("/Users/test")
    private val configDir = Path.of("/Users/test/.claude-quayside/work")
    private val work = AccountId("work")
    private val logger = RecordingLogger()
    private val runner =
        FakeProcessRunner { command ->
            val out =
                when {
                    command.argv.contains("--bg") -> "backgrounded · $ID · n"
                    command.argv.contains("--version") -> "${RuntimeRequirements.MIN_CLAUDE_VERSION} (Claude Code)"
                    command.argv.contains("security") || command.argv.contains("find-generic-password") -> TOKEN
                    else -> "[]"
                }
            Outcome.Ok(ProcessResult(0, out, ""))
        }

    private fun runtime(
        account: AccountId,
        dir: Path? = configDir,
        injectHookToken: Boolean = false,
    ): AgentRuntime =
        agentRuntimeModule(
            process = runner,
            clock = TestClock(),
            logger = logger,
            homeDir = home,
            injectHookToken = injectHookToken,
            account = account,
            configDir = dir,
        )

    private fun claudeEnvs(): List<Map<String, String>> =
        runner.commands.filter { it.argv.first() == "claude" }.map { it.env }

    @Test
    fun `start resume respawn and the read commands of an extra account get CLAUDE_CONFIG_DIR`() =
        runTest {
            val runtime = runtime(work)

            runtime.listSessions()
            runtime.start(aStartSpec(cwd = home))
            runtime.resume(ResumeSpec(ConversationId("5e2a9c41-0000-4000-8000-0000000000aa"), home))
            runtime.stop(SessionId(ID))
            runtime.respawn(SessionId(ID))
            runtime.recentOutput(SessionId(ID))
            runtime.remove(SessionId(ID))
            runtime.health()

            val envs = claudeEnvs()
            envs.size shouldBe EXPECTED_CALLS
            envs.forEach { it shouldBe mapOf("CLAUDE_CONFIG_DIR" to configDir.toString()) }
        }

    @Test
    fun `start of an extra account keeps AM_HOOK_TOKEN`() =
        runTest {
            runtime(work, injectHookToken = true).start(aStartSpec(cwd = home)) shouldBe Outcome.Ok(SessionId(ID))

            val start = runner.commands.single { it.argv.contains("--bg") }
            start.env shouldBe mapOf("AM_HOOK_TOKEN" to TOKEN, "CLAUDE_CONFIG_DIR" to configDir.toString())
        }

    @Test
    fun `respawn of an extra account keeps AM_HOOK_TOKEN`() =
        runTest {
            runtime(work, injectHookToken = true).respawn(SessionId(ID))

            val respawn = runner.commands.single { it.argv.contains("respawn") }
            respawn.env shouldBe mapOf("AM_HOOK_TOKEN" to TOKEN, "CLAUDE_CONFIG_DIR" to configDir.toString())
        }

    @Test
    fun `start of the default account with a token has the token only`() =
        runTest {
            runtime(AccountId.DEFAULT, injectHookToken = true).start(aStartSpec(cwd = home))

            runner.commands.single { it.argv.contains("--bg") }.env shouldBe mapOf("AM_HOOK_TOKEN" to TOKEN)
        }

    @Test
    fun `the default account never gets CLAUDE_CONFIG_DIR even when a dir is given`() =
        runTest {
            val runtime = runtime(AccountId.DEFAULT, dir = Path.of("/Users/test/.claude"))

            runtime.listSessions()
            runtime.start(aStartSpec(cwd = home))
            runtime.stop(SessionId(ID))
            runtime.health()

            claudeEnvs().forEach { it shouldBe emptyMap() }
            claudeEnvs().size shouldBe EXPECTED_CALLS_DEFAULT
        }

    @Test
    fun `the config dir and the token never reach the log`() =
        runTest {
            val runtime = runtime(work, injectHookToken = true)

            runtime.listSessions()
            runtime.start(aStartSpec(cwd = home))
            runtime.health()

            logger.entries.toString() shouldNotContain configDir.toString()
            logger.entries.toString() shouldNotContain TOKEN
        }

    private companion object {
        const val ID = "a1b2c3d4"
        const val TOKEN = "tok-secret-123"
        const val EXPECTED_CALLS = 10
        const val EXPECTED_CALLS_DEFAULT = 6
    }
}
