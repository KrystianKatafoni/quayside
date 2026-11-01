package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.aStartSpec
import dev.quayside.core.domain.LogLevel
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
import kotlin.time.Duration.Companion.seconds

/** `AM_HOOK_TOKEN` in the environment of `claude --bg` and `claude respawn` (NFR-4). */
class HookTokenEnvTest {
    private val home = Path.of("/Users/test")
    private val logger = RecordingLogger()
    private val runner =
        FakeProcessRunner { command ->
            val out = if (command.argv.contains("--bg")) "backgrounded · $ID · n" else ""
            Outcome.Ok(ProcessResult(0, out, ""))
        }

    private fun adapter(tokens: HookTokenStore?) =
        ClaudeCliAdapter(runner, TestClock(), logger, CliConfig(home, "claude", 10.seconds, 30.seconds, tokens))

    private val store = HookTokenStore { Outcome.Ok(HookToken(TOKEN)) }

    @Test
    fun `start passes the token in AM_HOOK_TOKEN`() =
        runTest {
            adapter(store).start(aStartSpec(cwd = home)) shouldBe Outcome.Ok(SessionId(ID))

            runner.commands.single().env shouldBe mapOf("AM_HOOK_TOKEN" to TOKEN)
        }

    @Test
    fun `respawn passes the token in AM_HOOK_TOKEN`() =
        runTest {
            adapter(store).respawn(SessionId(ID))

            runner.commands.single().env shouldBe mapOf("AM_HOOK_TOKEN" to TOKEN)
        }

    @Test
    fun `read-only commands get no token`() =
        runTest {
            val adapter = adapter(store)
            adapter.stop(SessionId(ID))
            adapter.recentOutput(SessionId(ID))

            runner.commands.forEach { it.env shouldBe emptyMap() }
        }

    @Test
    fun `without a store the environment stays empty`() =
        runTest {
            adapter(null).start(aStartSpec(cwd = home))

            runner.commands.single().env shouldBe emptyMap()
        }

    @Test
    fun `a keychain failure still starts the session, logs a warning and leaks nothing`() =
        runTest {
            val failing = HookTokenStore { Outcome.Err(HookTokenError.KEYCHAIN_UNAVAILABLE) }

            adapter(failing).start(aStartSpec(cwd = home)) shouldBe Outcome.Ok(SessionId(ID))

            runner.commands.single().env shouldBe emptyMap()
            logger.entries.any { it.level == LogLevel.WARN } shouldBe true
            logger.entries.toString() shouldNotContain TOKEN
        }

    @Test
    fun `the module injects the keychain token only when asked`() =
        runTest {
            val keychain =
                FakeProcessRunner { command ->
                    val out = if (command.argv.contains("--bg")) "backgrounded · $ID · n" else TOKEN
                    Outcome.Ok(ProcessResult(0, out, ""))
                }

            agentRuntimeModule(keychain, TestClock(), logger, home, injectHookToken = true)
                .start(aStartSpec(cwd = home))
            agentRuntimeModule(keychain, TestClock(), logger, home).start(aStartSpec(cwd = home))

            val bg = keychain.commands.filter { it.argv.contains("--bg") }
            bg.map { it.env } shouldBe listOf(mapOf("AM_HOOK_TOKEN" to TOKEN), emptyMap())
        }

    private companion object {
        const val ID = "a1b2c3d4"
        const val TOKEN = "tok-secret-123"
    }
}
