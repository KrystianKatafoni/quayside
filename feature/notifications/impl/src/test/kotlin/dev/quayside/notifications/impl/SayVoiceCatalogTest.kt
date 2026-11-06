package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.notifications.api.VoiceCatalogError
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/** [SayVoiceCatalog] and [voiceCatalogModule] over a scripted [FakeProcessRunner]: no real `say`. */
class SayVoiceCatalogTest {
    private val home = Path.of("/Users/test")

    private fun ok(
        stdout: String,
        exit: Int = 0,
        truncated: Boolean = false,
    ): Outcome<ProcessResult, ProcessError> = Outcome.Ok(ProcessResult(exit, stdout, "secret stderr", truncated))

    @Test
    fun `runs say with the voice list option in the work dir with a 10 s timeout`() =
        runTest {
            val runner = FakeProcessRunner { ok("Zosia pl_PL # Witaj") }

            SayVoiceCatalog(runner, home).voices()

            runner.calls.size shouldBe 1
            runner.commands.single() shouldBe ShellCommand(listOf("/usr/bin/say", "-v", "?"), home)
            runner.calls
                .single()
                .second.timeout shouldBe 10.seconds
        }

    @Test
    fun `the module runs the same command and parses the output`() =
        runTest {
            val runner = FakeProcessRunner { ok("Zosia pl_PL # Witaj\nAlex en_US # Hi") }

            val result = voiceCatalogModule(runner, home).voices()

            result shouldBe
                Outcome.Ok(
                    listOf(
                        VoiceOption(VoiceId("Alex"), "en_US", "Hi"),
                        VoiceOption(VoiceId("Zosia"), "pl_PL", "Witaj"),
                    ),
                )
            runner.commands.single() shouldBe ShellCommand(listOf("/usr/bin/say", "-v", "?"), home)
            runner.calls
                .single()
                .second.timeout shouldBe 10.seconds
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unavailable")
    fun `not found and start failure map to UNAVAILABLE`(error: ProcessError) =
        runTest {
            val result = SayVoiceCatalog(FakeProcessRunner { Outcome.Err(error) }, home).voices()

            result shouldBe Outcome.Err(VoiceCatalogError.UNAVAILABLE)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failed")
    fun `timeout, io failure and non zero exit map to FAILED`(response: Outcome<ProcessResult, ProcessError>) =
        runTest {
            val result = SayVoiceCatalog(FakeProcessRunner { response }, home).voices()

            result shouldBe Outcome.Err(VoiceCatalogError.FAILED)
        }

    @Test
    fun `empty output gives an empty list`() =
        runTest {
            SayVoiceCatalog(FakeProcessRunner { ok("") }, home).voices() shouldBe Outcome.Ok(emptyList<VoiceOption>())
        }

    @Test
    fun `output cut at the limit is still parsed`() =
        runTest {
            val runner = FakeProcessRunner { ok("Zosia pl_PL # Witaj\nAl", truncated = true) }

            SayVoiceCatalog(runner, home).voices() shouldBe
                Outcome.Ok(listOf(VoiceOption(VoiceId("Zosia"), "pl_PL", "Witaj")))
        }

    @Test
    fun `a custom program is used as the command`() =
        runTest {
            val runner = FakeProcessRunner { ok("") }

            SayVoiceCatalog(runner, home, "/tmp/fake-say").voices()

            runner.commands.single().argv shouldBe listOf("/tmp/fake-say", "-v", "?")
        }

    companion object {
        @JvmStatic
        fun unavailable(): List<Arguments> =
            listOf(
                arguments(ProcessError.NotFound("say")),
                arguments(ProcessError.StartFailed("no such directory")),
            )

        @JvmStatic
        fun failed(): List<Arguments> =
            listOf(
                arguments(Outcome.Err(ProcessError.Timeout(10.seconds))),
                arguments(Outcome.Err(ProcessError.IoFailed("broken pipe"))),
                arguments(Outcome.Ok(ProcessResult(1, "Zosia pl_PL # Witaj", "boom"))),
                arguments(Outcome.Ok(ProcessResult(127, "", "say: not found"))),
            )
    }
}
