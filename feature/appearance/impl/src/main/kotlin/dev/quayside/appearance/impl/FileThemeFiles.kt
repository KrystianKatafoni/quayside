package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.CustomScheme
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
import java.nio.file.Path

private val pretty = Json { prettyPrint = true }

/**
 * [ThemeFiles] on the file system: reads files up to 64 KiB strictly, writes theme.json v1 through a temp file in the
 * directory of the target and an atomic move. The JDK creates the temp file with mode 0600, so an exported file is
 * readable by its owner only. Logs only fixed categories, never paths or contents.
 */
internal class FileThemeFiles(
    private val catalog: () -> ThemeCatalog,
    private val io: CoroutineDispatcher,
    private val logger: Logger,
) : ThemeFiles {
    override suspend fun read(source: Path): Outcome<AppearanceSettings, ThemeFileError> =
        withContext(io) {
            val outcome: Outcome<AppearanceSettings, ThemeFileError> =
                when (val data = readThemeData(source)) {
                    is Outcome.Err -> data
                    is Outcome.Ok -> settingsOf(data.value)
                }
            logged(outcome)
        }

    override suspend fun readSchemes(source: Path): Outcome<List<CustomScheme>, ThemeFileError> =
        withContext(io) {
            val outcome: Outcome<List<CustomScheme>, ThemeFileError> =
                when (val data = readThemeData(source)) {
                    is Outcome.Err -> data
                    is Outcome.Ok -> schemesOf(data.value)
                }
            logged(outcome)
        }

    private fun settingsOf(data: JsonObject): Outcome<AppearanceSettings, ThemeFileError> {
        val decoded = decodeSettings(data, catalog())
        return if (decoded.invalid.isEmpty()) Outcome.Ok(decoded.settings) else Outcome.Err(ThemeFileError.InvalidValue)
    }

    private fun schemesOf(data: JsonObject): Outcome<List<CustomScheme>, ThemeFileError> {
        val decoded = decodeSchemes(data)
        return if (decoded.invalid) Outcome.Err(ThemeFileError.InvalidValue) else Outcome.Ok(decoded.schemes)
    }

    private fun <T> logged(outcome: Outcome<T, ThemeFileError>): Outcome<T, ThemeFileError> {
        if (outcome is Outcome.Err) logger.warn("theme file not read", "reason" to outcome.error::class.simpleName)
        return outcome
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

    private fun envelopeText(settings: AppearanceSettings): String =
        pretty.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put(SCHEMA_VERSION, JsonPrimitive(CURRENT_VERSION))
                put(DATA, encodeSettings(settings))
            },
        )
}
