package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessRunner
import dev.quayside.core.process.RunOptions
import dev.quayside.notifications.api.VoiceCatalog
import dev.quayside.notifications.api.VoiceCatalogError
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** [VoiceCatalog] over `say -v ?` run by [runner] in [workDir]; [program] is injectable for tests. */
internal class SayVoiceCatalog(
    private val runner: ProcessRunner,
    private val workDir: Path,
    private val program: String = "/usr/bin/say",
) : VoiceCatalog {
    override suspend fun voices(): Outcome<List<VoiceOption>, VoiceCatalogError> =
        when (val result = runner.run(ShellCommand(listOf(program, "-v", "?"), workDir), RunOptions(TIMEOUT))) {
            is Outcome.Err -> {
                Outcome.Err(unavailableOrFailed(result.error))
            }

            is Outcome.Ok -> {
                if (result.value.exitCode == 0) {
                    Outcome.Ok(parseSayVoices(result.value.stdout))
                } else {
                    Outcome.Err(VoiceCatalogError.FAILED)
                }
            }
        }

    private fun unavailableOrFailed(error: ProcessError): VoiceCatalogError =
        when (error) {
            is ProcessError.NotFound, is ProcessError.StartFailed -> VoiceCatalogError.UNAVAILABLE
            is ProcessError.Timeout, is ProcessError.IoFailed -> VoiceCatalogError.FAILED
        }

    private companion object {
        val TIMEOUT = 10.seconds
    }
}

private val SAY_LINE = Regex("""^(.+?)\s+([a-z]{2,3}_[A-Z0-9]{2,3})\s+#""")

/**
 * The voices of `say -v ?` output: lines it does not understand are skipped, the result is sorted by name ignoring
 * case and keeps the first voice of each exact name.
 */
internal fun parseSayVoices(output: String): List<VoiceOption> =
    output
        .lineSequence()
        .mapNotNull { line -> SAY_LINE.find(line)?.destructured }
        .filter { (name) -> name.isNotBlank() }
        .map { (name, locale) -> VoiceOption(VoiceId(name.trim()), locale) }
        .distinctBy { it.id }
        .sortedBy { it.id.name.lowercase() }
        .toList()
