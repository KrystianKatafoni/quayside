package dev.quayside.summaries.impl

import dev.quayside.agentruntime.api.FakeOneShotPrompt
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.storage.JsonStorage
import dev.quayside.summaries.api.Summarizer
import dev.quayside.summaries.api.SummarizerContract
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors

/** The summarizer given out by `summariesModule` passes the shared contract (ENG-T2). */
class LastMessageSummarizerContractTest : SummarizerContract() {
    @TempDir
    lateinit var dir: Path

    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    @Suppress("ForbiddenMethodCall") // the contract is not suspending; runBlocking is allowed in tests
    override fun summarizer(): Summarizer =
        runBlocking { summariesModule(JsonStorage(dir, io), FakeOneShotPrompt(), NoOpLogger) }.summarizer
}
