package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.EventMeta
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds

@Suppress("ForbiddenMethodCall") // runBlocking is allowed in tests (ENG-C3).
class KtorHookServerTest {
    private val logger = RecordingLogger()
    private val server = KtorHookServer(TestClock(), logger, port = 0)
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun tearDown() {
        server.stop()
        io.close()
    }

    private suspend fun startClient(): HookClient = HookClient((server.start(TEST_TOKEN) as Outcome.Ok).value)

    private val stop =
        """{"session_id":"c-1","cwd":"/work/app","hook_event_name":"Stop","last_assistant_message":"Done"}"""

    @Test
    fun `a valid hook is answered 200 and published with the receive time`() =
        runTest {
            val client = startClient()
            val received = async(start = CoroutineStart.UNDISPATCHED) { server.events.first() }

            val status = withContext(io) { client.post(stop) }

            status shouldBe 200
            received.await() shouldBe
                AgentEvent.TurnEnded(EventMeta(ConversationId("c-1"), Path.of("/work/app"), T0), "Done")
        }

    @Test
    fun `a wrong or missing token is 401 and publishes nothing`() =
        runTest {
            val client = startClient()
            val received =
                async(
                    start = CoroutineStart.UNDISPATCHED,
                ) { withTimeoutOrNull(300.milliseconds) { server.events.first() } }

            val statuses =
                withContext(io) { listOf(client.post(stop, token = "nope"), client.post(stop, token = null)) }

            statuses shouldBe listOf(401, 401)
            received.await() shouldBe null
        }

    @Test
    fun `a token that is only a prefix of the real one is rejected`() =
        runTest {
            val client = startClient()

            withContext(io) { client.post(stop, token = TEST_TOKEN.value.dropLast(1)) } shouldBe 401
        }

    @Test
    fun `an unusable body is 400 and only the reason is logged`() =
        runTest {
            val client = startClient()

            val status = withContext(io) { client.post("""{"secret-prompt": nope""") }

            status shouldBe 400
            val warn = logger.entriesAt(LogLevel.WARN).single()
            warn.fields shouldBe mapOf("reason" to HookRejection.MALFORMED)
            warn.message.contains("secret") shouldBe false
        }

    @Test
    fun `a hook with nothing to publish is 200`() =
        runTest {
            val client = startClient()

            withContext(
                io,
            ) { client.post("""{"session_id":"c","cwd":"/a","hook_event_name":"SessionStart"}""") } shouldBe
                200
        }

    @Test
    fun `a body above the limit is 413`() =
        runTest {
            val client = startClient()

            withContext(io) { client.post("x".repeat(1024 * 1024 + 1)) } shouldBe 413
        }

    @Test
    fun `a request without a body length is 411`() =
        runTest {
            val client = startClient()

            withContext(io) { client.postChunked(stop) } shouldBe 411
        }

    @Test
    fun `only POST on hooks is served`() =
        runTest {
            val client = startClient()

            val statuses = withContext(io) { listOf(client.get(), client.post(stop, path = "/other")) }

            statuses shouldBe listOf(405, 404)
        }

    @Test
    fun `an event with no collector is dropped without an error`() =
        runTest {
            val client = startClient()

            withContext(io) { client.post(stop) } shouldBe 200
            logger.entriesAt(LogLevel.WARN) shouldBe emptyList()
        }

    @Test
    fun `the log never contains the free text of an event`() =
        runTest {
            val client = startClient()
            val received = async(start = CoroutineStart.UNDISPATCHED) { server.events.first() }

            withContext(io) { client.post(stop) }
            received.await()

            logger.entries.any { entry -> entry.fields.values.any { it.toString().contains("Done") } } shouldBe false
        }

    @Test
    fun `listens on loopback only`() {
        val port = server.startBlocking()
        val external =
            NetworkInterface
                .getNetworkInterfaces()
                .toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress && it is java.net.Inet4Address }
        assumeTrue(external != null, "no non-loopback address on this machine")

        val refused = runCatching { Socket(external as InetAddress, port).close() }.exceptionOrNull()

        (refused is ConnectException) shouldBe true
    }

    @Test
    fun `a port that is taken is reported as a bind failure`() {
        val first = server.startBlocking()
        val second = KtorHookServer(TestClock(), logger, port = first)

        try {
            runBlocking { second.start(TEST_TOKEN) } shouldBe Outcome.Err(HookServerError.BIND_FAILED)
        } finally {
            second.stop()
        }
    }

    @Test
    fun `stop is safe when the server never started`() {
        KtorHookServer(TestClock(), logger, port = 0).stop()
    }
}
