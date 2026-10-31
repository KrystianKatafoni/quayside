package dev.agentmanager.projects.impl

import dev.agentmanager.core.domain.NoOpLogger
import dev.agentmanager.core.storage.JsonStorage
import dev.agentmanager.projects.api.ProjectsApi
import dev.agentmanager.projects.api.ProjectsApiContract
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import java.nio.file.Path
import java.util.concurrent.Executors

/**
 * [ProjectsApiContract] on the real store: `projects.json` in `storage`, next to the contract's
 * `workspace` in the same temp directory (ENG-T2).
 */
class JsonProjectStoreContractTest : ProjectsApiContract() {
    // Real threads: JsonStorage and toRealPath() block on file IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    // The contract's factory is not suspending; runBlocking is allowed in tests (ENG-C3).
    @Suppress("ForbiddenMethodCall")
    override fun create(tempDir: Path): ProjectsApi =
        runBlocking { projectsModule(JsonStorage(tempDir.resolve("storage"), io), io, NoOpLogger) }
}
