package dev.agentmanager.app

import dev.agentmanager.core.domain.AppDispatchers
import dev.agentmanager.core.domain.Clock
import dev.agentmanager.core.domain.Logger
import dev.agentmanager.core.domain.SystemClock
import dev.agentmanager.core.process.LoginShellProcessRunner
import dev.agentmanager.core.process.ProcessRunner
import dev.agentmanager.core.storage.JsonStorage
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

/** Directory of the app's JSON files, e.g. `projects.json`: `~/Library/Application Support/AgentManager` (AM-304). */
internal fun appSupportDir(userHome: Path): Path =
    userHome.resolve("Library").resolve("Application Support").resolve("AgentManager")
