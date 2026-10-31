package dev.quayside.app

import dev.quayside.core.domain.AppDispatchers
import dev.quayside.core.domain.Clock
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.SystemClock
import dev.quayside.core.process.LoginShellProcessRunner
import dev.quayside.core.process.ProcessRunner
import dev.quayside.core.storage.JsonStorage
import kotlinx.coroutines.Dispatchers
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * Shared infrastructure handed to every module factory (docs/11 §9).
 *
 * @property homeDir the user's home directory, where Claude Code keeps its files.
 * @property process runs commands in the user's login shell, so `claude` and `git` are found as in Terminal.
 * @property storage JSON files of the app in [appSupportDir].
 */
internal data class CoreDeps(
    val clock: Clock,
    val dispatchers: AppDispatchers,
    val logger: Logger,
    val homeDir: Path,
    val process: ProcessRunner,
    val storage: JsonStorage,
)

/** Real clock, dispatchers, logging, processes and files. Picked only here (ENG-C6). */
@Suppress("InjectDispatcher")
internal fun productionCoreDeps(): CoreDeps {
    val dispatchers = AppDispatchers(main = Dispatchers.Main, io = Dispatchers.IO, default = Dispatchers.Default)
    val logger = Slf4jLogger(LoggerFactory.getLogger("am"))
    val home = Path.of(System.getProperty("user.home"))
    return CoreDeps(
        clock = SystemClock,
        dispatchers = dispatchers,
        logger = logger,
        homeDir = home,
        process = LoginShellProcessRunner(io = dispatchers.io, logger = logger),
        storage = JsonStorage(appSupportDir(home), dispatchers.io, logger),
    )
}

/** Directory of the app's JSON files, e.g. `projects.json`: `~/Library/Application Support/Quayside` (AM-304). */
internal fun appSupportDir(userHome: Path): Path =
    userHome.resolve("Library").resolve("Application Support").resolve("Quayside")
