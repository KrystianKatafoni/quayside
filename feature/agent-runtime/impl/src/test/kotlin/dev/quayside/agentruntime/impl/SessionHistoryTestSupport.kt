package dev.quayside.agentruntime.impl

import dev.quayside.core.testing.LogEntry
import java.nio.file.Path

/** Lower-case text of everything a log entry carries, without underscores, so "NOT_JSONL" and "notJsonl" match. */
internal fun LogEntry.flat(): String =
    (listOf(message) + fields.map { (key, value) -> "$key=$value" })
        .joinToString(" ")
        .lowercase()
        .replace("_", "")

/** The number a log entry attaches to the skip category [category] (e.g. "NOT_JSONL"), null when it has none. */
internal fun List<LogEntry>.countOf(category: String): Int? {
    val wanted = category.lowercase().replace("_", "")
    return firstNotNullOfOrNull { entry ->
        entry.fields.entries
            .firstOrNull { (key, value) -> wanted in key.lowercase().replace("_", "") && value is Int }
            ?.value as? Int
    }
}

internal fun List<LogEntry>.mention(text: String): Boolean =
    any { it.flat().contains(text.lowercase().replace("_", "")) }

internal fun List<LogEntry>.mention(path: Path): Boolean =
    any { it.flat().contains(path.toString().lowercase().replace("_", "")) }
