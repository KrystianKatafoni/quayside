package dev.quayside.appearance.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** The adapter that asks macOS through `defaults` (ADR 0023, point 4). */
class DefaultsSystemModeAdapterTest {
    private val home = Path.of("/Users/test")

    @Test
    fun `runs defaults read -g AppleInterfaceStyle with a short timeout`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Ok(ProcessResult(0, "Dark\n", "")) }

            val mode = DefaultsSystemModeAdapter(runner, home).read()

            mode shouldBe true
            val (command, options) = runner.calls.single()
            command.argv shouldBe listOf("defaults", "read", "-g", "AppleInterfaceStyle")
            command.workDir shouldBe home
            options.timeout shouldBe 2.seconds
        }

    @Test
    fun `reads again on every call`() =
        runTest {
            var answer: Outcome<ProcessResult, ProcessError> = Outcome.Ok(ProcessResult(0, "Dark\n", ""))
            val runner = FakeProcessRunner { answer }
            val adapter = DefaultsSystemModeAdapter(runner, home)

            val first = adapter.read()
            answer = Outcome.Ok(ProcessResult(1, "", "does not exist"))
            val second = adapter.read()
            answer = Outcome.Err(ProcessError.Timeout(2.seconds))
            val third = adapter.read()

            listOf(first, second, third) shouldBe listOf(true, false, null)
            runner.calls.size shouldBe 3
        }
}
