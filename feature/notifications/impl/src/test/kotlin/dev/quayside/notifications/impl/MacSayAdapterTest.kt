package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.notifications.api.VoiceId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** [MacSayAdapter] over a scripted [FakeProcessRunner]: the argv, the retry and the error mapping (FR-N4). */
class MacSayAdapterTest {
    private val workDir = Path.of("/work")

    private fun ok(code: Int = 0): Outcome<ProcessResult, ProcessError> = Outcome.Ok(ProcessResult(code, "", ""))

    private fun adapter(runner: FakeProcessRunner) = MacSayAdapter(runner, workDir)

    private fun utterance(
        text: String = "Hello",
        voice: VoiceId? = VoiceId("Zosia"),
        rate: Int = 190,
        volume: Int = 70,
    ) = Utterance(text, voice, rate, volume)

    @Test
    fun `passes voice rate and volume`() =
        runTest {
            val runner = FakeProcessRunner()

            val result = adapter(runner).speak(utterance())

            result shouldBe Outcome.Ok(Unit)
            runner.commands.single() shouldBe
                ShellCommand(
                    listOf("/usr/bin/say", "-v", "Zosia", "-r", "190", "[[volm 0.70]] Hello"),
                    workDir,
                )
        }

    @Test
    fun `runs say with a timeout of 60 seconds`() =
        runTest {
            val runner = FakeProcessRunner()

            adapter(runner).speak(utterance())

            runner.calls
                .single()
                .second.timeout shouldBe 60.seconds
        }

    @Test
    fun `uses the given program`() =
        runTest {
            val runner = FakeProcessRunner()

            MacSayAdapter(runner, workDir, "/opt/fake/say").speak(utterance())

            runner.commands
                .single()
                .argv
                .first() shouldBe "/opt/fake/say"
        }

    @Test
    fun `system voice omits -v`() =
        runTest {
            val runner = FakeProcessRunner()

            adapter(runner).speak(utterance(voice = null))

            runner.commands.single().argv shouldBe listOf("/usr/bin/say", "-r", "190", "[[volm 0.70]] Hello")
        }

    @Test
    fun `a text starting with a dash stays the last argument`() =
        runTest {
            val runner = FakeProcessRunner()

            adapter(runner).speak(utterance(text = "-v Alex hi"))

            runner.commands
                .single()
                .argv
                .last() shouldBe "[[volm 0.70]] -v Alex hi"
        }

    @ParameterizedTest(name = "volume {0} gives {1}")
    @CsvSource("0,0.00", "5,0.05", "70,0.70", "100,1.00")
    fun `volume is written with a dot and two digits`(
        volume: Int,
        expected: String,
    ) = runTest {
        val runner = FakeProcessRunner()

        adapter(runner).speak(utterance(volume = volume))

        runner.commands
            .single()
            .argv
            .last() shouldBe "[[volm $expected]] Hello"
    }

    @Test
    fun `retries without voice when the voice is missing`() =
        runTest {
            val runner = FakeProcessRunner { if ("-v" in it.argv) ok(1) else ok(0) }

            val result = adapter(runner).speak(utterance())

            result shouldBe Outcome.Ok(Unit)
            runner.commands.map { it.argv } shouldBe
                listOf(
                    listOf("/usr/bin/say", "-v", "Zosia", "-r", "190", "[[volm 0.70]] Hello"),
                    listOf("/usr/bin/say", "-r", "190", "[[volm 0.70]] Hello"),
                )
        }

    @Test
    fun `without fallback a failed voice is not retried`() =
        runTest {
            val runner = FakeProcessRunner { ok(1) }

            val result = MacSayAdapter(runner, workDir, fallbackToSystemVoice = false).speak(utterance())

            result shouldBe Outcome.Err(SpeechError.Failed(1))
            runner.commands.size shouldBe 1
        }

    @Test
    fun `fails with the code of the retry when it fails too`() =
        runTest {
            val runner = FakeProcessRunner { if ("-v" in it.argv) ok(1) else ok(2) }

            adapter(runner).speak(utterance()) shouldBe Outcome.Err(SpeechError.Failed(2))
            runner.commands.size shouldBe 2
        }

    @Test
    fun `the system voice is not retried`() =
        runTest {
            val runner = FakeProcessRunner { ok(3) }

            adapter(runner).speak(utterance(voice = null)) shouldBe Outcome.Err(SpeechError.Failed(3))
            runner.commands.size shouldBe 1
        }

    @Test
    fun `not found maps to Unavailable`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Err(ProcessError.NotFound("/usr/bin/say")) }

            adapter(runner).speak(utterance()) shouldBe Outcome.Err(SpeechError.Unavailable)
            runner.commands.size shouldBe 1
        }

    @Test
    fun `timeout maps to TimedOut`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Err(ProcessError.Timeout(60.seconds)) }

            adapter(runner).speak(utterance()) shouldBe Outcome.Err(SpeechError.TimedOut)
            runner.commands.size shouldBe 1
        }

    @Test
    fun `non zero exit maps to Failed`() =
        runTest {
            val runner = FakeProcessRunner { ok(5) }

            adapter(runner).speak(utterance(voice = null)) shouldBe Outcome.Err(SpeechError.Failed(5))
        }

    @Test
    fun `utterance toString hides the text`() {
        val text = utterance(text = "secret question").toString()

        text shouldNotContain "secret"
        text shouldNotContain "question"
    }
}
