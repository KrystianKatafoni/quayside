package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** [AfplayAdapter] over a scripted [FakeProcessRunner]: argv, timeout, the Glass fallback and the error mapping. */
class AfplayAdapterTest {
    private val workDir = Path.of("/work")

    private fun ok(code: Int = 0): Outcome<ProcessResult, ProcessError> = Outcome.Ok(ProcessResult(code, "", ""))

    private fun adapter(runner: FakeProcessRunner) = AfplayAdapter(runner, workDir)

    private fun file(name: String) = "/System/Library/Sounds/$name.aiff"

    @Test
    fun `plays the file of the sound`() =
        runTest {
            val runner = FakeProcessRunner()

            val result = adapter(runner).play(SystemSound.BASSO)

            result shouldBe Outcome.Ok(Unit)
            runner.commands.single() shouldBe ShellCommand(listOf("/usr/bin/afplay", file("Basso")), workDir)
        }

    @Test
    fun `uses a 10 s timeout`() =
        runTest {
            val runner = FakeProcessRunner()

            adapter(runner).play(SystemSound.HERO)

            runner.calls
                .single()
                .second.timeout shouldBe 10.seconds
        }

    @Test
    fun `uses the given program and sounds directory`() =
        runTest {
            val runner = FakeProcessRunner()

            AfplayAdapter(runner, workDir, Path.of("/opt/my sounds"), "/opt/fake/afplay").play(SystemSound.POP)

            runner.commands.single().argv shouldBe listOf("/opt/fake/afplay", "/opt/my sounds/Pop.aiff")
        }

    @Test
    fun `non zero exit retries with the fallback sound`() =
        runTest {
            val runner = FakeProcessRunner { if (file("Basso") in it.argv) ok(1) else ok(0) }

            val result = adapter(runner).play(SystemSound.BASSO)

            result shouldBe Outcome.Ok(Unit)
            runner.commands.map { it.argv } shouldBe
                listOf(
                    listOf("/usr/bin/afplay", file("Basso")),
                    listOf("/usr/bin/afplay", file("Glass")),
                )
        }

    @Test
    fun `fallback sound is not retried`() =
        runTest {
            val runner = FakeProcessRunner { ok(1) }

            val result = adapter(runner).play(SystemSound.GLASS)

            result shouldBe Outcome.Err(SoundError.Failed(1))
            runner.commands.size shouldBe 1
        }

    @Test
    fun `failed fallback maps to Failed with its exit code`() =
        runTest {
            val runner = FakeProcessRunner { if (file("Hero") in it.argv) ok(1) else ok(2) }

            adapter(runner).play(SystemSound.HERO) shouldBe Outcome.Err(SoundError.Failed(2))
            runner.commands.size shouldBe 2
        }

    @Test
    fun `a successful first try is not retried`() =
        runTest {
            val runner = FakeProcessRunner { ok(0) }

            adapter(runner).play(SystemSound.PING)

            runner.commands.size shouldBe 1
        }

    @Test
    fun `not found maps to Unavailable`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Err(ProcessError.NotFound("/usr/bin/afplay")) }

            adapter(runner).play(SystemSound.PING) shouldBe Outcome.Err(SoundError.Unavailable)
            runner.commands.size shouldBe 1
        }

    @Test
    fun `start failed maps to Unavailable`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Err(ProcessError.StartFailed("no dir")) }

            adapter(runner).play(SystemSound.PING) shouldBe Outcome.Err(SoundError.Unavailable)
            runner.commands.size shouldBe 1
        }

    @Test
    fun `io failed maps to Unavailable`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Err(ProcessError.IoFailed("pipe")) }

            adapter(runner).play(SystemSound.PING) shouldBe Outcome.Err(SoundError.Unavailable)
            runner.commands.size shouldBe 1
        }

    @Test
    fun `timeout maps to TimedOut`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Err(ProcessError.Timeout(10.seconds)) }

            adapter(runner).play(SystemSound.BASSO) shouldBe Outcome.Err(SoundError.TimedOut)
            runner.commands.size shouldBe 1
        }

    @Test
    fun `an error of the fallback try is mapped too`() =
        runTest {
            val runner =
                FakeProcessRunner {
                    if (file("Basso") in it.argv) ok(1) else Outcome.Err(ProcessError.Timeout(10.seconds))
                }

            adapter(runner).play(SystemSound.BASSO) shouldBe Outcome.Err(SoundError.TimedOut)
            runner.commands.size shouldBe 2
        }
}
