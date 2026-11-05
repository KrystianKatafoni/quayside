package dev.quayside.appearance.impl

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.domain.warn
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.process.ProcessRunner
import dev.quayside.core.process.RunOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Where the macOS mode is read from. */
internal fun interface SystemModeSource {
    /** true = dark, false = light, null = could not be read. Never throws. */
    suspend fun read(): Boolean?
}

private const val EXIT_OK = 0
private const val EXIT_KEY_MISSING = 1
private const val DARK = "Dark"

/**
 * `defaults read -g AppleInterfaceStyle`: exit 0 with `Dark` in the last non-empty line = dark; exit 0 with another
 * word or exit 1 (no key) = light; any other exit code or any [ProcessError] = null.
 */
internal fun parseInterfaceStyle(result: Outcome<ProcessResult, ProcessError>): Boolean? =
    when (result) {
        is Outcome.Err -> {
            null
        }

        is Outcome.Ok -> {
            when (result.value.exitCode) {
                EXIT_OK -> {
                    result.value.stdout
                        .lines()
                        .lastOrNull { it.isNotBlank() }
                        ?.trim() == DARK
                }

                EXIT_KEY_MISSING -> {
                    false
                }

                else -> {
                    null
                }
            }
        }
    }

/** Limit of one `defaults` run; a stuck profile must not stall the poll. */
private val READ_TIMEOUT = 2.seconds

/** [SystemModeSource] on `defaults read -g AppleInterfaceStyle` run through [process] in [home], limit 2 s. */
internal class DefaultsSystemModeAdapter(
    private val process: ProcessRunner,
    private val home: Path,
) : SystemModeSource {
    override suspend fun read(): Boolean? =
        parseInterfaceStyle(
            process.run(
                ShellCommand(listOf("defaults", "read", "-g", "AppleInterfaceStyle"), home),
                RunOptions(timeout = READ_TIMEOUT),
            ),
        )
}

/**
 * The last known macOS mode in [mode]. While `settings.dayNight.enabled` is true [source] is read at [start] and then
 * every [poll] in [scope]; while it is false nothing is read. A failed read keeps the last known mode and is logged
 * once per streak of failures (a fixed category, no output of the process).
 */
internal class SystemModeWatcher(
    private val source: SystemModeSource,
    private val settings: StateFlow<AppearanceSettings>,
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val poll: Duration = 5.seconds,
) {
    private val state = MutableStateFlow<Boolean?>(null)
    private var failing = false

    /** The last known mode; null until the first successful read. */
    val mode: StateFlow<Boolean?> = state.asStateFlow()

    /** Reads the mode once when day/night is on (before returning) and starts following [settings] in [scope]. */
    suspend fun start() {
        val enabledAtStart = settings.value.dayNight.enabled
        if (enabledAtStart) readOnce()
        scope.launch { follow(skipFirstRead = enabledAtStart) }
    }

    /** Polls while day/night is on; [skipFirstRead] = [start] already read for the state it saw. */
    private suspend fun follow(skipFirstRead: Boolean) {
        var skipRead = skipFirstRead
        settings.map { it.dayNight.enabled }.distinctUntilChanged().collectLatest { enabled ->
            val readNow = !skipRead
            skipRead = false
            if (enabled) pollWhileOn(readNow)
        }
    }

    private suspend fun pollWhileOn(readNow: Boolean) {
        if (readNow) readOnce()
        while (true) {
            delay(poll)
            readOnce()
        }
    }

    private suspend fun readOnce() {
        val read = source.read()
        if (read != null) {
            failing = false
            state.value = read
        } else if (!failing) {
            failing = true
            logger.warn("macOS mode could not be read")
        }
    }
}
