package dev.quayside.terminal.impl.replyspike

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessResult
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

private fun entry(
    id: String,
    name: String,
) = """{"id":"$id","kind":"background","name":"$name","state":"working"}"""

/**
 * Cleanup of `TrialRunner` over `FakeProcessRunner`. Record mode (`ReplySpikeMain.record`) is not covered here:
 * its cleanup (try/finally) sits in a private function of `main`, so review checks it, including the case when
 * `waitBlocked` returns null.
 */
internal class TrialRunnerTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `cleanup stops and removes every session with the name`() =
        runTest {
            var name = ""
            val processes =
                FakeProcessRunner { command ->
                    val argv = command.argv
                    if (argv.getOrNull(1) == "--bg") name = argv[argv.indexOf("--name") + 1]
                    val listing = "[${entry("0001", name)},${entry("0002", name)}]"
                    Outcome.Ok(ProcessResult(0, if (argv.getOrNull(1) == "agents") listing else "", ""))
                }
            val logger = RecordingLogger()
            val hidden = HiddenReply(Path.of("/nonexistent/sh"), emptyMap(), logger)
            val runner = TrialRunner(SpikeClaude(processes), hidden, logger, root)

            runner.trial(Kind.TEXT, 0, TrialSettings(blockedTimeout = 100.milliseconds))

            name.startsWith("am507-") shouldBe true
            val commands = processes.commands.map { it.argv.drop(1) }
            commands shouldContainAll
                listOf(listOf("stop", "0001"), listOf("rm", "0001"), listOf("stop", "0002"), listOf("rm", "0002"))
            commands.count { it.firstOrNull() == "rm" } shouldBe 2
        }
}
