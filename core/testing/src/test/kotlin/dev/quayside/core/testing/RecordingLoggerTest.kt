package dev.quayside.core.testing

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.info
import dev.quayside.core.domain.warn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RecordingLoggerTest {
    @Test
    fun `records entries in order and filters by level`() {
        val logger = RecordingLogger()

        logger.info("started", "sessionId" to aSessionId())
        logger.warn("slow", "ms" to 2500)

        logger.entries shouldHaveSize 2
        logger.entries.first() shouldBe LogEntry(LogLevel.INFO, "started", mapOf("sessionId" to aSessionId()), null)
        logger.entriesAt(LogLevel.WARN).map { it.message } shouldBe listOf("slow")
    }
}
