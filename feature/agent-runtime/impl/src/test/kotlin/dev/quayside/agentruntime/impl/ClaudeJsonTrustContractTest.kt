package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.WorkspaceTrust
import dev.quayside.agentruntime.api.WorkspaceTrustContract
import dev.quayside.core.domain.NoOpLogger
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/** [WorkspaceTrustContract] on the file-based implementation over a `.claude.json` in a temporary directory. */
@Suppress("InjectDispatcher")
class ClaudeJsonTrustContractTest : WorkspaceTrustContract() {
    @TempDir
    lateinit var dir: Path

    override val existingDir: Path get() = dir.resolve("project").createDirectories()
    override val missingDir: Path get() = dir.resolve("nope")

    override fun create(): WorkspaceTrust {
        val home = dir.resolve("home").createDirectories()
        home.resolve(".claude.json").writeText("""{"projects":{}}""")
        return workspaceTrustModule(home, Dispatchers.IO, NoOpLogger)
    }
}
