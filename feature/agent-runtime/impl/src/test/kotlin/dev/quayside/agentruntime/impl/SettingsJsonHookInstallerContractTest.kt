package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.HookInstaller
import dev.quayside.agentruntime.api.HookInstallerContract
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.testing.TestClock
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories

/** [HookInstallerContract] on the file-based installer over a temporary home and project (ENG-T2). */
@Suppress("InjectDispatcher")
class SettingsJsonHookInstallerContractTest : HookInstallerContract() {
    @TempDir
    lateinit var dir: Path

    override val projectDir: Path get() = dir.resolve("project").createDirectories()

    override fun create(): HookInstaller =
        hookInstallerModule(
            homeDir = dir.resolve("home").createDirectories(),
            backupDir = dir.resolve("hooks-backup"),
            clock = TestClock(),
            io = Dispatchers.IO,
            logger = NoOpLogger,
        )
}
