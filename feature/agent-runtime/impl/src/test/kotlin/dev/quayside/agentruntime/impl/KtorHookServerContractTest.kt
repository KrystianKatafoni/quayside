package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.AgentEventSource
import dev.quayside.agentruntime.api.AgentEventSourceContract
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.testing.TestClock
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import java.util.concurrent.Executors

/** [AgentEventSourceContract] on the real server: [emit] posts the hook Claude Code would send. */
class KtorHookServerContractTest : AgentEventSourceContract() {
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val servers = mutableListOf<KtorHookServer>()
    private lateinit var client: HookClient

    @AfterEach
    fun tearDown() {
        servers.forEach { it.stop() }
        io.close()
    }

    override fun create(): AgentEventSource {
        val server = KtorHookServer(TestClock(), NoOpLogger, port = 0).also { servers += it }
        client = HookClient(server.startBlocking())
        return server
    }

    override suspend fun emit(event: AgentEvent) {
        withContext(io) { client.post(event.toHookJson()) }
    }
}

private fun AgentEvent.toHookJson(): String {
    val head = """"session_id":"${meta.conversationId.value}","cwd":"${meta.cwd}""""
    val tail =
        when (this) {
            is AgentEvent.NeedsInput -> {
                """"hook_event_name":"Notification","notification_type":"agent_needs_input","message":"$question""""
            }

            is AgentEvent.PermissionRequested -> {
                """"hook_event_name":"Notification","notification_type":"permission_prompt",""" +
                    """"message":"Claude needs your permission to use $tool""""
            }

            is AgentEvent.TurnEnded -> {
                """"hook_event_name":"Stop","last_assistant_message":"$lastMessage""""
            }

            is AgentEvent.Failed -> {
                """"hook_event_name":"StopFailure","error_type":"$kind""""
            }

            is AgentEvent.SessionEnded -> {
                """"hook_event_name":"SessionEnd","reason":"$reason""""
            }
        }
    return "{$head,$tail}"
}
