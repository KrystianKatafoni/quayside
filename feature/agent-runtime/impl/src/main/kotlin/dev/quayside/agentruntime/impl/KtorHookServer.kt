package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.AgentEventSource
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.debug
import dev.quayside.core.domain.warn
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Receives Claude Code HTTP hooks on `127.0.0.1` and publishes them as [AgentEvent]s (ADR 0004).
 *
 * Only `POST /hooks` with the right `X-AM-Token` is accepted (NFR-4). Replies are plain status
 * codes without a body, so no input text is echoed back. The stream is hot and has no replay: an
 * event nobody collects is dropped, and polling corrects the state.
 *
 * @param port TCP port; 0 picks a free one (tests), the real one comes from [start].
 */
internal class KtorHookServer(
    private val clock: Clock,
    private val logger: Logger,
    private val port: Int,
) : AgentEventSource {
    private val flow = MutableSharedFlow<AgentEvent>(extraBufferCapacity = BUFFER)
    private var server: EmbeddedServer<*, *>? = null

    // Own scope for the engine: its failures end up in the handler (and in start's result), never in the caller.
    private val engineScope =
        CoroutineScope(
            SupervisorJob() +
                CoroutineExceptionHandler { _, cause ->
                    logger.warn("hook server failure", "type" to cause::class.simpleName)
                },
        )

    @Volatile
    private var token: HookToken? = null

    override val events: Flow<AgentEvent> = flow.asSharedFlow()

    /** Starts listening once, accepting [expected] as the token; returns the bound port. Not concurrent-safe. */
    suspend fun start(expected: HookToken): Outcome<Int, HookServerError> {
        token = expected
        val bound = bind()
        if (bound is Outcome.Err) stop()
        return bound
    }

    // CIO binds in the background: a bind failure cancels its startup job, so it surfaces as a
    // CancellationException from the await. Only our own cancellation may propagate.
    private suspend fun bind(): Outcome<Int, HookServerError> =
        try {
            val engine = engineScope.embeddedServer(CIO, port, LOOPBACK, emptyList(), EmptyCoroutineContext) { hooks() }
            server = engine
            engine.startSuspend(wait = false)
            Outcome.Ok(
                engine.engine
                    .resolvedConnectors()
                    .first()
                    .port,
            )
        } catch (ignored: CancellationException) {
            currentCoroutineContext().ensureActive()
            Outcome.Err(HookServerError.BIND_FAILED)
        } catch (ignored: IOException) {
            Outcome.Err(HookServerError.BIND_FAILED)
        } catch (ignored: IllegalStateException) {
            Outcome.Err(HookServerError.BIND_FAILED)
        }

    /** Stops accepting requests; safe to call when not started. */
    fun stop() {
        server?.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MS)
        server = null
        engineScope.cancel()
    }

    private fun Application.hooks() {
        routing { post(PATH) { handle(call) } }
    }

    private suspend fun handle(call: ApplicationCall) {
        val length = call.request.contentLength()
        val status =
            when {
                !tokenMatches(call.request.headers[TOKEN_HEADER], token) -> HttpStatusCode.Unauthorized
                length == null -> HttpStatusCode.LengthRequired
                length > MAX_BODY_BYTES -> HttpStatusCode.PayloadTooLarge
                else -> publish(call.receiveText())
            }
        call.respond(status)
    }

    private fun publish(body: String): HttpStatusCode =
        when (val parsed = HookPayloadParser.parse(body, clock.now())) {
            is HookParse.Invalid -> {
                logger.warn("hook rejected", "reason" to parsed.reason)
                HttpStatusCode.BadRequest
            }

            HookParse.Ignored -> {
                HttpStatusCode.OK
            }

            is HookParse.Event -> {
                // Free texts of the event are DEBUG only (ENG-C8), so log the type, not the event.
                logger.debug("hook received", "type" to parsed.event::class.simpleName)
                if (!flow.tryEmit(parsed.event)) logger.warn("hook event dropped", "reason" to "buffer-full")
                HttpStatusCode.OK
            }
        }

    // Constant-time comparison; a missing header or token never matches.
    private fun tokenMatches(
        presented: String?,
        expected: HookToken?,
    ): Boolean =
        presented != null &&
            expected != null &&
            MessageDigest.isEqual(presented.toByteArray(Charsets.UTF_8), expected.value.toByteArray(Charsets.UTF_8))

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val PATH = "/hooks"
        const val TOKEN_HEADER = "X-AM-Token"
        const val BUFFER = 64
        const val MAX_BODY_BYTES = 1024L * 1024
        const val STOP_TIMEOUT_MS = 1_000L
    }
}

/** Why the hook server did not start; a constant category (ENG-C8). */
internal enum class HookServerError { BIND_FAILED, }
