package dev.quayside.terminal.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files

/** The bell stream of [FakeTerminalApi] (AM-509): what other features' tests rely on. */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeTerminalApiBellTest {
    private val api = FakeTerminalApi()

    private suspend fun session(): FakeTerminalSession {
        val dir = Files.createTempDirectory("bell")
        return (api.openShell(dir) as Outcome.Ok).value as FakeTerminalSession
    }

    @Test
    fun `a bell reaches the collector with the id of its session`() =
        runTest {
            val session = session()
            val seen = mutableListOf<TerminalId>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { api.bells.toList(seen) }

            api.ringBell(session)

            seen shouldBe listOf(session.id)
            job.cancel()
        }

    @Test
    fun `a bell without a collector is lost`() =
        runTest {
            val session = session()
            api.ringBell(session)
            val seen = mutableListOf<TerminalId>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { api.bells.toList(seen) }

            seen shouldBe emptyList()
            job.cancel()
        }
}
