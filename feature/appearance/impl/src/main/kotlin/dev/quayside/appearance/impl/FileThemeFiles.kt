package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.ThemeCatalog
import dev.quayside.appearance.api.ThemeFileError
import dev.quayside.appearance.api.ThemeFiles
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.warn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val SIZE_LIMIT = 64 * 1024
private const val SCHEMA_VERSION = "schemaVersion"
private const val DATA = "data"
private const val CURRENT_VERSION = 1

private val pretty = Json { prettyPrint = true }

/**
 * [ThemeFiles] on the file system: reads files up to 64 KiB strictly, writes theme.json v1 through a temp file in the
 * directory of the target and an atomic move. The JDK creates the temp file with mode 0600, so an exported file is
 * readable by its owner only. Logs only fixed categories, never paths or contents.
 */
internal class FileThemeFiles(
    private val catalog: ThemeCatalog,
    private val io: CoroutineDispatcher,
    private val logger: Logger,
) : ThemeFiles {
    override suspend fun read(source: Path): Outcome<AppearanceSettings, ThemeFileError> =
        withContext(io) {
            val bytes = readBytes(source)
            val outcome = if (bytes == null) failed(ThemeFileError.ReadFailed) else parse(bytes)
            if (outcome is Outcome.Err) logger.warn("theme file not read", "reason" to outcome.error::class.simpleName)
            outcome
        }

    override suspend fun write(
        settings: AppearanceSettings,
        target: Path,
    ): Outcome<Unit, ThemeFileError> =
        withContext(io) {
            val text = envelopeText(settings)
            val result = writeAtomically(text, target)
            if (result is Outcome.Err) logger.warn("theme file not written")
            result
        }

    /** The bytes of [source], or null when it cannot be read or is over the limit (at most limit + 1 are read). */
    private fun readBytes(source: Path): ByteArray? =
        try {
            Files.newInputStream(source).use { it.readNBytes(SIZE_LIMIT + 1) }.takeIf { it.size <= SIZE_LIMIT }
        } catch (_: IOException) {
            null
        }

    private fun parse(bytes: ByteArray): Outcome<AppearanceSettings, ThemeFileError> {
        val root = parseRoot(bytes) ?: return failed(ThemeFileError.NotThemeFile)
        val version = (root[SCHEMA_VERSION] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        val data = root[DATA] as? JsonObject
        return when {
            version == null || version < 1 -> {
                failed(ThemeFileError.NotThemeFile)
            }

            version > CURRENT_VERSION -> {
                failed(ThemeFileError.NewerVersion(version))
            }

            data == null -> {
                failed(ThemeFileError.NotThemeFile)
            }

            else -> {
                decodeSettings(data, catalog).let {
                    if (it.invalid.isEmpty()) Outcome.Ok(it.settings) else failed(ThemeFileError.InvalidValue)
                }
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

    private fun envelopeText(settings: AppearanceSettings): String =
        pretty.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put(SCHEMA_VERSION, JsonPrimitive(CURRENT_VERSION))
                put(DATA, encodeSettings(settings))
            },
        )

    private fun writeAtomically(
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
            failed(ThemeFileError.WriteFailed)
        } finally {
            temp?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun <T> failed(error: ThemeFileError): Outcome<T, ThemeFileError> = Outcome.Err(error)
}
