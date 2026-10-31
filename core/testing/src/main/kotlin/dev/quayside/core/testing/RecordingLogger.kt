package dev.quayside.core.testing

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Logger

/** One captured log call. */
public data class LogEntry(
    val level: LogLevel,
    val message: String,
    val fields: Map<String, Any?>,
    val cause: Throwable?,
)

/** [Logger] that keeps every entry so tests can assert on what was logged. */
public class RecordingLogger : Logger {
    private val recorded = mutableListOf<LogEntry>()

    public val entries: List<LogEntry> get() = recorded.toList()

    override fun log(
        level: LogLevel,
        message: String,
        fields: Map<String, Any?>,
        cause: Throwable?,
    ) {
        recorded += LogEntry(level, message, fields, cause)
    }

    public fun entriesAt(level: LogLevel): List<LogEntry> = recorded.filter { it.level == level }
}
