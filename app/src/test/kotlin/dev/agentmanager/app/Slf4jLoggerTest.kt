package dev.agentmanager.app

import dev.agentmanager.core.domain.LogLevel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.slf4j.event.EventRecordingLogger
import org.slf4j.event.Level
import org.slf4j.event.SubstituteLoggingEvent
import org.slf4j.helpers.SubstituteLogger
import java.util.concurrent.LinkedBlockingQueue

class Slf4jLoggerTest {
    private val events = LinkedBlockingQueue<SubstituteLoggingEvent>()
    private val logger = Slf4jLogger(EventRecordingLogger(SubstituteLogger("am", events, false), events))

    @Test
    fun `maps every level and keeps the cause`() {
        val cause = IllegalStateException("boom")

        LogLevel.entries.forEach { logger.log(it, "msg-$it", emptyMap(), cause) }

        events.map { it.level } shouldBe listOf(Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR)
        events.map { it.message } shouldBe LogLevel.entries.map { "msg-$it" }
        events.map { it.throwable }.distinct() shouldBe listOf(cause)
    }

    @Test
    fun `appends structured fields as key-value pairs`() {
        Slf4jLogger.format("started", emptyMap()) shouldBe "started"
        Slf4jLogger.format("started", mapOf("sessionId" to "7c5dcf5d", "pid" to null)) shouldBe
            "started sessionId=7c5dcf5d pid=null"
    }
}
