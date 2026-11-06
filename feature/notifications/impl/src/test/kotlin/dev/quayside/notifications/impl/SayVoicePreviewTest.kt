package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoicePreviewError
import dev.quayside.notifications.api.VoiceSample
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** [SayVoicePreview] and [voicePreviewModule] over a scripted [FakeProcessRunner]: no real `say`, no sound (FR-N4). */
class SayVoicePreviewTest {
    private val workDir = Path.of("/work")

    private fun ok(code: Int = 0): Outcome<ProcessResult, ProcessError> = Outcome.Ok(ProcessResult(code, "", ""))

    private fun sample(
        text: String = "Witaj",
        voice: VoiceId? = VoiceId("Zosia"),
        rate: Int = 250,
        volume: Int = 40,
    ) = VoiceSample(voice, text, rate, volume)

    @Test
    fun `plays with voice, rate and volume`() =
        runTest {
            val runner = FakeProcessRunner()

            val result = SayVoicePreview(runner, workDir).play(sample())

            result shouldBe Outcome.Ok(Unit)
            runner.commands.single() shouldBe
                ShellCommand(listOf("/usr/bin/say", "-v", "Zosia", "-r", "250", "[[volm 0.40]] Witaj"), workDir)
        }

    @Test
    fun `system voice has no -v`() =
        runTest {
            val runner = FakeProcessRunner()

            SayVoicePreview(runner, workDir).play(sample(voice = null))

            runner.commands.single().argv shouldBe listOf("/usr/bin/say", "-r", "250", "[[volm 0.40]] Witaj")
        }

    @Test
    fun `exit code other than 0 is FAILED and the voice is not retried`() =
        runTest {
            val runner = FakeProcessRunner { ok(1) }

            val result = SayVoicePreview(runner, workDir).play(sample())

            result shouldBe Outcome.Err(VoicePreviewError.FAILED)
            runner.commands.size shouldBe 1
        }

    @Test
    fun `not found is UNAVAILABLE`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Err(ProcessError.NotFound("/usr/bin/say")) }

            SayVoicePreview(runner, workDir).play(sample()) shouldBe Outcome.Err(VoicePreviewError.UNAVAILABLE)
        }

    @Test
    fun `start failure is UNAVAILABLE`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Err(ProcessError.StartFailed("no such directory")) }

            SayVoicePreview(runner, workDir).play(sample()) shouldBe Outcome.Err(VoicePreviewError.UNAVAILABLE)
        }

    @Test
    fun `timeout is FAILED`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Err(ProcessError.Timeout(60.seconds)) }

            SayVoicePreview(runner, workDir).play(sample()) shouldBe Outcome.Err(VoicePreviewError.FAILED)
        }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["[[rate 500]] x", "x ]] y", "[[", "]]"])
    fun `embedded command is refused without a process`(text: String) =
        runTest {
            val runner = FakeProcessRunner()

            val result = SayVoicePreview(runner, workDir).play(sample(text = text))

            result shouldBe Outcome.Err(VoicePreviewError.FAILED)
            runner.commands.size shouldBe 0
        }

    @Test
    fun `a long sample is played`() =
        runTest {
            val runner = FakeProcessRunner()
            val text = "a".repeat(LONG)

            val result = SayVoicePreview(runner, workDir).play(sample(text = text))

            result shouldBe Outcome.Ok(Unit)
            runner.commands
                .single()
                .argv
                .last() shouldBe "[[volm 0.40]] $text"
        }

    @Test
    fun `uses the given program`() =
        runTest {
            val runner = FakeProcessRunner()

            SayVoicePreview(runner, workDir, "/opt/fake/say").play(sample())

            runner.commands
                .single()
                .argv
                .first() shouldBe "/opt/fake/say"
        }

    @Test
    fun `voicePreviewModule runs usr bin say in workDir with a timeout of 60 seconds`() =
        runTest {
            val runner = FakeProcessRunner()

            val result = voicePreviewModule(runner, workDir).play(sample())

            result shouldBe Outcome.Ok(Unit)
            runner.commands.single() shouldBe
                ShellCommand(listOf("/usr/bin/say", "-v", "Zosia", "-r", "250", "[[volm 0.40]] Witaj"), workDir)
            runner.calls
                .single()
                .second.timeout shouldBe 60.seconds
        }

    private companion object {
        const val LONG = 250
    }
}
