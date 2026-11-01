package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class HookTokenTest {
    private val home = Path.of("/home/test")

    private fun result(
        exit: Int,
        stdout: String = "",
    ): Outcome<ProcessResult, ProcessError> = Outcome.Ok(ProcessResult(exit, stdout, ""))

    @Test
    fun `an existing token is read from the keychain and nothing is written`() =
        runTest {
            val runner = FakeProcessRunner { result(0, "stored-token\n") }

            val token = KeychainHookTokenStore(runner, home, newToken = { "new" }).getOrCreate()

            token shouldBe Outcome.Ok(HookToken("stored-token"))
            runner.commands shouldHaveSize 1
            runner.commands.single().argv shouldBe
                listOf("security", "find-generic-password", "-s", "Quayside", "-a", "hook-token", "-w")
        }

    @Test
    fun `a missing item creates and stores a new token`() =
        runTest {
            val runner = FakeProcessRunner { if ("find-generic-password" in it.argv) result(44) else result(0) }

            val token = KeychainHookTokenStore(runner, home, newToken = { "fresh" }).getOrCreate()

            token shouldBe Outcome.Ok(HookToken("fresh"))
            runner.commands.last().argv shouldBe
                listOf("security", "add-generic-password", "-s", "Quayside", "-a", "hook-token", "-w", "fresh", "-U")
        }

    @Test
    fun `a keychain that refuses the read is an error and creates nothing`() =
        runTest {
            val runner = FakeProcessRunner { result(36) }

            val token = KeychainHookTokenStore(runner, home).getOrCreate()

            token shouldBe Outcome.Err(HookTokenError.KEYCHAIN_REJECTED)
            runner.commands shouldHaveSize 1
        }

    @Test
    fun `an empty stored secret is an error`() =
        runTest {
            val runner = FakeProcessRunner { result(0, "\n") }

            KeychainHookTokenStore(runner, home).getOrCreate() shouldBe Outcome.Err(HookTokenError.KEYCHAIN_REJECTED)
        }

    @Test
    fun `a keychain that refuses the write is an error`() =
        runTest {
            val runner = FakeProcessRunner { if ("find-generic-password" in it.argv) result(44) else result(1) }

            KeychainHookTokenStore(runner, home).getOrCreate() shouldBe Outcome.Err(HookTokenError.KEYCHAIN_REJECTED)
        }

    @Test
    fun `a missing security tool or timeout is unavailable`() =
        runTest {
            val notFound = FakeProcessRunner { Outcome.Err(ProcessError.NotFound("security")) }
            val timeout = FakeProcessRunner { Outcome.Err(ProcessError.Timeout(10.seconds)) }
            val writeFails =
                FakeProcessRunner {
                    if ("find-generic-password" in it.argv) result(44) else Outcome.Err(ProcessError.IoFailed("x"))
                }

            listOf(notFound, timeout, writeFails).map { KeychainHookTokenStore(it, home).getOrCreate() } shouldBe
                List(3) { Outcome.Err(HookTokenError.KEYCHAIN_UNAVAILABLE) }
        }

    @Test
    fun `random tokens are long url-safe and different`() {
        val tokens = List(20) { randomToken() }

        tokens.toSet() shouldHaveSize 20
        tokens.all { it.length == 43 && Regex("[A-Za-z0-9_-]+").matches(it) } shouldBe true
    }

    @Test
    fun `the token is hidden in toString`() {
        HookToken("secret").toString() shouldBe "HookToken(***)"
    }
}
