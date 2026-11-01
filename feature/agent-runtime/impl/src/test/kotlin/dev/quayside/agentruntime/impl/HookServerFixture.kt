package dev.quayside.agentruntime.impl

import dev.quayside.core.domain.Outcome
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Token the hook server tests configure. */
internal val TEST_TOKEN = HookToken("test-token")

/** Plain JDK client so the tests need no HTTP library; always talks to 127.0.0.1. */
internal class HookClient(
    private val port: Int,
) {
    private val client = HttpClient.newHttpClient()

    /** Posts [body] to [path] with [token] (omitted when null) and returns the status code. */
    fun post(
        body: String,
        token: String? = TEST_TOKEN.value,
        path: String = "/hooks",
    ): Int {
        val request =
            HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:$port$path"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .apply { if (token != null) header("X-AM-Token", token) }
                .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
    }

    /** A request without a body length (chunked transfer). */
    fun postChunked(body: String): Int {
        val request =
            HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:$port/hooks"))
                .POST(HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofString(body)))
                .header("X-AM-Token", TEST_TOKEN.value)
                .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
    }

    fun get(path: String = "/hooks"): Int {
        val request =
            HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:$port$path"))
                .header("X-AM-Token", TEST_TOKEN.value)
                .GET()
                .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()
    }
}

/** Starts the server for tests that are not suspending and returns its port. */
@Suppress("ForbiddenMethodCall") // runBlocking is allowed in tests (ENG-C3).
internal fun KtorHookServer.startBlocking(): Int = (runBlocking { start(TEST_TOKEN) } as Outcome.Ok).value
