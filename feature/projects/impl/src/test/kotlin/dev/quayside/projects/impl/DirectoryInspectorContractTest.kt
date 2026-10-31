package dev.quayside.projects.impl

import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.process.LoginShellProcessRunner
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.InspectionWorld
import dev.quayside.projects.api.PathKind
import dev.quayside.projects.api.ProjectInspector
import dev.quayside.projects.api.ProjectInspectorContract
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.jupiter.api.AfterEach
import java.nio.file.Path
import java.util.concurrent.Executors

/**
 * [ProjectInspectorContract] on the real inspector with the real `git` and real files in the contract's
 * temp directory (ENG-T2). Only the runtime is fake: tests never run the real Claude Code.
 */
class DirectoryInspectorContractTest : ProjectInspectorContract() {
    // Real threads: git processes and file IO block, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    override fun create(
        tempDir: Path,
        world: InspectionWorld,
    ): ProjectInspector {
        val runtimeWorld = DiskWorld.build(tempDir.resolve("project"), world, TestGit(home = tempDir))
        return projectInspectorModule(
            projects =
                FakeProjectsApi(
                    initial = world.registered,
                    pathKind = PathKind::onDisk,
                    realPath = PathKind::realPathOnDisk,
                ),
            runtime = FakeAgentRuntime(runtimeWorld),
            process = LoginShellProcessRunner(io, shell = Path.of("/bin/sh")),
            io = io,
            logger = NoOpLogger,
        )
    }
}
