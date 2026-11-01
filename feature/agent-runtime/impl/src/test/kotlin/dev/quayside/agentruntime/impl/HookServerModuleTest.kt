package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.TestClock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

@Suppress("InjectDispatcher", "ForbiddenMethodCall")
class HookServerModuleTest {
    private val logger = RecordingLogger()
    private val job = Job()
    private val scope = CoroutineScope(job + Dispatchers.Default)

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun listening(port: Int): Boolean = runCatching { Socket("127.0.0.1", port).close() }.isSuccess

    private suspend fun awaitListening(
        port: Int,
        expected: Boolean,
    ): Boolean {
        repeat(WAIT_TRIES) {
            if (listening(port) == expected) return expected
            delay(50.milliseconds)
        }
        return !expected
    }

    private suspend fun awaitErrorLog() {
        repeat(WAIT_TRIES) {
            if (logger.entriesAt(LogLevel.ERROR).isNotEmpty()) return
            delay(50.milliseconds)
        }
    }

    @Test
    fun `serves hooks after start and stops when the scope is cancelled`() =
        runBlocking {
            val port = freePort()
            startHookServer({ Outcome.Ok(TEST_TOKEN) }, TestClock(), logger, scope, port)

            awaitListening(port, expected = true) shouldBe true
            HookClient(port).post("""{"session_id":"c","cwd":"/a","hook_event_name":"SessionStart"}""") shouldBe 200

            job.cancelAndJoin()

            awaitListening(port, expected = false) shouldBe false
        }

    @Test
    fun `a token error is logged and nothing listens`() =
        runBlocking {
            val port = freePort()
            startHookServer({ Outcome.Err(HookTokenError.KEYCHAIN_UNAVAILABLE) }, TestClock(), logger, scope, port)

            awaitErrorLog()

            listening(port) shouldBe false
            logger.entriesAt(LogLevel.ERROR).single().fields shouldBe
                mapOf("reason" to HookTokenError.KEYCHAIN_UNAVAILABLE)
            job.cancelAndJoin()
        }

    @Test
    fun `a taken port is logged and does not crash the scope`() =
        runBlocking {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { taken ->
                startHookServer({ Outcome.Ok(TEST_TOKEN) }, TestClock(), logger, scope, taken.localPort)

                awaitErrorLog()

                logger.entriesAt(LogLevel.ERROR).single().fields shouldBe mapOf("reason" to HookServerError.BIND_FAILED)
                job.isActive shouldBe true
            }
            job.cancelAndJoin()
        }

    @Test
    fun `the public factory takes its token from the keychain`() =
        runBlocking {
            val port = freePort()
            val keychain =
                FakeProcessRunner { Outcome.Ok(ProcessResult(0, "from-keychain\n", "")) }
            hookEventSourceModule(keychain, TestClock(), logger, scope, Path.of("/home/test"), port)

            awaitListening(port, expected = true) shouldBe true
            val client = HookClient(port)
            val body = """{"session_id":"c","cwd":"/a","hook_event_name":"SessionStart"}"""
            client.post(body, token = "from-keychain") shouldBe 200
            client.post(body, token = TEST_TOKEN.value) shouldBe 401
            job.cancelAndJoin()
        }

    @Test
    fun `a missing security tool leaves the stream silent`() =
        runBlocking {
            val port = freePort()
            val runner = FakeProcessRunner { Outcome.Err(ProcessError.NotFound("security")) }
            hookEventSourceModule(runner, TestClock(), logger, scope, Path.of("/home/test"), port)

            delay(300.milliseconds)

            listening(port) shouldBe false
            job.cancelAndJoin()
        }

    private companion object {
        const val WAIT_TRIES = 100
    }
}
