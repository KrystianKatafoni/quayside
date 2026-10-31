package dev.agentmanager.app

import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.domain.Logger

/** [Logger] backed by SLF4J. Structured fields are appended as `key=value` pairs after the message. */
internal class Slf4jLogger(
    private val delegate: org.slf4j.Logger,
) : Logger {
    override fun log(
        level: LogLevel,
        message: String,
        fields: Map<String, Any?>,
        cause: Throwable?,
    ) {
        val line = format(message, fields)
        when (level) {
            LogLevel.DEBUG -> delegate.debug(line, cause)
            LogLevel.INFO -> delegate.info(line, cause)
            LogLevel.WARN -> delegate.warn(line, cause)
            LogLevel.ERROR -> delegate.error(line, cause)
        }
    }

    companion object {
        internal fun format(
            message: String,
            fields: Map<String, Any?>,
        ): String =
            if (fields.isEmpty()) {
                message
            } else {
                fields.entries.joinToString(separator = " ", prefix = "$message ") { (key, value) -> "$key=$value" }
            }
    }
}
