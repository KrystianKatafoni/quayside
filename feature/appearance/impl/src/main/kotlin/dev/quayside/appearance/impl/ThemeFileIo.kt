package dev.quayside.appearance.impl

import dev.quayside.appearance.api.ThemeFileError
import dev.quayside.core.domain.Outcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal const val SIZE_LIMIT = 64 * 1024
internal const val SCHEMA_VERSION = "schemaVersion"
internal const val DATA = "data"
internal const val CURRENT_VERSION = 1

/** The `data` object of the theme file at [source], read strictly; errors are the categories of [ThemeFileError]. */
internal fun readThemeData(source: Path): Outcome<JsonObject, ThemeFileError> {
    val bytes = readBytes(source) ?: return Outcome.Err(ThemeFileError.ReadFailed)
    return parse(bytes)
}

/** The bytes of [source], or null when it cannot be read or is over the limit (at most limit + 1 are read). */
private fun readBytes(source: Path): ByteArray? =
    try {
        Files.newInputStream(source).use { it.readNBytes(SIZE_LIMIT + 1) }.takeIf { it.size <= SIZE_LIMIT }
    } catch (_: IOException) {
        null
    }

private fun parse(bytes: ByteArray): Outcome<JsonObject, ThemeFileError> {
    val root = parseRoot(bytes) ?: return Outcome.Err(ThemeFileError.NotThemeFile)
    val version = (root[SCHEMA_VERSION] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
    val data = root[DATA] as? JsonObject
    return when {
        version == null || version < 1 -> {
            Outcome.Err(ThemeFileError.NotThemeFile)
        }

        version > CURRENT_VERSION -> {
            Outcome.Err(ThemeFileError.NewerVersion(version))
        }

        data == null -> {
            Outcome.Err(ThemeFileError.NotThemeFile)
        }

        else -> {
            Outcome.Ok(data)
        }
    }
}

// Parser messages quote the input, so a failure is only a null here (ENG-C8).
private fun parseRoot(bytes: ByteArray): JsonObject? =
    try {
        val text =
            Charsets.UTF_8
                .newDecoder()
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: CharacterCodingException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

internal fun writeAtomically(
    text: String,
    target: Path,
): Outcome<Unit, ThemeFileError> {
    var temp: Path? = null
    return try {
        val created = Files.createTempFile(target.toAbsolutePath().parent, ".theme-", ".tmp")
        temp = created
        Files.writeString(created, text)
        Files.move(created, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        Outcome.Ok(Unit)
    } catch (_: IOException) {
        Outcome.Err(ThemeFileError.WriteFailed)
    } finally {
        temp?.let { runCatching { Files.deleteIfExists(it) } }
    }
}
