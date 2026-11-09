package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.ProcessRunner
import dev.quayside.notifications.api.SoundId
import dev.quayside.notifications.api.SoundLibrary
import dev.quayside.notifications.api.SoundLibraryError
import dev.quayside.notifications.api.SoundSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

/**
 * [SoundLibrary] over the `*.aiff` files of [soundsDir], listed on [io], and [AfplayAdapter] for the preview (no
 * fallback sound: a missing file is [SoundLibraryError.FAILED]). Files whose name is not a valid [SoundId] are left
 * out of the list.
 */
internal class AfplaySoundLibrary(
    runner: ProcessRunner,
    workDir: Path,
    private val io: CoroutineDispatcher,
    private val soundsDir: Path = Path.of("/System/Library/Sounds"),
    program: String = "/usr/bin/afplay",
) : SoundLibrary {
    private val player = AfplayAdapter(runner, workDir, soundsDir, program, fallback = null)

    override suspend fun sounds(): Outcome<List<SoundId>, SoundLibraryError> =
        withContext(io) {
            try {
                Files.list(soundsDir).use { files ->
                    val names =
                        files
                            .filter { it.extension.equals("aiff", ignoreCase = true) }
                            .map { it.nameWithoutExtension }
                            .toList()
                    Outcome.Ok(names.mapNotNull(SoundId::parse).sortedBy { it.name.lowercase() })
                }
            } catch (_: java.io.IOException) {
                Outcome.Err(SoundLibraryError.UNAVAILABLE)
            }
        }

    override suspend fun play(
        sound: SoundId,
        volume: Int,
    ): Outcome<Unit, SoundLibraryError> {
        require(volume in SoundSettings.MIN_VOLUME..SoundSettings.MAX_VOLUME) { "volume out of range" }
        return when (val result = player.play(sound, volume)) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Err -> Outcome.Err(error(result.error))
        }
    }

    private fun error(error: SoundError): SoundLibraryError =
        when (error) {
            SoundError.Unavailable -> SoundLibraryError.UNAVAILABLE
            is SoundError.Failed, SoundError.TimedOut -> SoundLibraryError.FAILED
        }
}
