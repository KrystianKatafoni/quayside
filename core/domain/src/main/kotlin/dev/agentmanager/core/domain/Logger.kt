package dev.agentmanager.core.domain

/** Log severity, lowest first. */
public enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * The single logging abstraction (ENG-C8). Use structured [fields] such as `sessionId` and
 * `projectId` instead of string interpolation, and never log tokens or prompt text above DEBUG.
 */
public interface Logger {
    public fun log(
        level: LogLevel,
        message: String,
        fields: Map<String, Any?> = emptyMap(),
        cause: Throwable? = null,
    )
}

public fun Logger.debug(
    message: String,
    vararg fields: Pair<String, Any?>,
): Unit = log(LogLevel.DEBUG, message, fields.toMap())

public fun Logger.info(
    message: String,
    vararg fields: Pair<String, Any?>,
): Unit = log(LogLevel.INFO, message, fields.toMap())

public fun Logger.warn(
    message: String,
    vararg fields: Pair<String, Any?>,
    cause: Throwable? = null,
): Unit = log(LogLevel.WARN, message, fields.toMap(), cause)

public fun Logger.error(
    message: String,
    vararg fields: Pair<String, Any?>,
    cause: Throwable? = null,
): Unit = log(LogLevel.ERROR, message, fields.toMap(), cause)

/** Discards everything; default for code paths that do not care about logs. */
public object NoOpLogger : Logger {
    override fun log(
        level: LogLevel,
        message: String,
        fields: Map<String, Any?>,
        cause: Throwable?,
    ): Unit = Unit
}
