package dev.quayside.core.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LoggerTest {
    private data class Entry(
        val level: LogLevel,
        val message: String,
        val fields: Map<String, Any?>,
        val cause: Throwable?,
    )

    private class RecordingLogger : Logger {
        val entries = mutableListOf<Entry>()

        override fun log(
            level: LogLevel,
            message: String,
            fields: Map<String, Any?>,
            cause: Throwable?,
        ) {
            entries += Entry(level, message, fields, cause)
        }
    }

    @Test
    fun `helpers pass level, message and structured fields`() {
        val logger = RecordingLogger()
        val failure = IllegalStateException("boom")

        logger.debug("polled", "sessions" to 3)
        logger.info("started", "sessionId" to SessionId("7c5dcf5d"))
        logger.warn("slow", "ms" to 2500)
        logger.error("failed", "projectId" to ProjectId("am"), cause = failure)

        logger.entries shouldBe
            listOf(
                Entry(LogLevel.DEBUG, "polled", mapOf("sessions" to 3), null),
                Entry(LogLevel.INFO, "started", mapOf("sessionId" to SessionId("7c5dcf5d")), null),
                Entry(LogLevel.WARN, "slow", mapOf("ms" to 2500), null),
                Entry(LogLevel.ERROR, "failed", mapOf("projectId" to ProjectId("am")), failure),
            )
    }

    @Test
    fun `no-op logger accepts everything`() {
        NoOpLogger.error("ignored", "k" to "v", cause = RuntimeException())
        NoOpLogger.error("ignored without cause")
        NoOpLogger.log(LogLevel.INFO, "defaults for fields and cause")
    }
}
