package dev.quayside.agentruntime.impl

import dev.quayside.agentruntime.api.OneShotPrompt
import dev.quayside.agentruntime.api.OneShotPromptContract
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.core.process.LoginShellProcessRunner
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories

/**
 * [OneShotPromptContract] on the real adapter, the real [LoginShellProcessRunner] and the fake `claude`
 * from [FakeClaude] (docs/12 §3).
 */
class ClaudePrintAdapterContractTest : OneShotPromptContract() {
    @TempDir
    lateinit var dir: Path

    // Real threads: the runner blocks on process IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private var claude: FakeClaude? = null

    @AfterEach
    fun cleanUp() {
        claude?.let { killAll(it.pids("hang.pid")) }
        io.close()
    }

    override fun create(
        behaviour: Behaviour,
        answer: String,
    ): OneShotPrompt {
        val world = RuntimeWorld(installed = behaviour != Behaviour.NOT_INSTALLED)
        val fake = FakeClaude.install(dir.resolve("claude"), world).also { claude = it }
        script(fake, behaviour, answer)
        return oneShotPromptModule(
            process = LoginShellProcessRunner(io, TEST_SHELL),
            homeDir = dir.resolve("home").createDirectories(),
            executable = fake.executable,
        )
    }

    private fun script(
        fake: FakeClaude,
        behaviour: Behaviour,
        answer: String,
    ) {
        when (behaviour) {
            Behaviour.ANSWERS -> {
                fake.setPrintResult("""{"is_error":false,"result":${JsonPrimitive(answer)}}""")
            }

            Behaviour.SLOW -> {
                fake.setMode("hang")
            }

            Behaviour.FAILS -> {
                fake.setMode(
                    "fail 3 Error: cannot run '$SECRET' in /Users/jan/repo\nkey sk-ant-x rejected",
                )
            }

            // The executable does not exist, so there is nothing to script.
            Behaviour.NOT_INSTALLED -> {
                fake.setMode("normal")
            }
        }
    }
}
