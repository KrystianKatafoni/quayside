package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.LoginShellProcessRunner
import dev.quayside.notifications.api.VoiceCatalogError
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.readLines
import kotlin.io.path.writeText

/**
 * [SayVoiceCatalog] with the real [LoginShellProcessRunner] and a fake `say` script in a temp directory (docs/12 §4).
 * No real `say`: the script prints a list like `say -v ?` and records its arguments.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SayVoiceCatalogIntegrationTest {
    @TempDir
    lateinit var dir: Path

    // Real threads: the runner blocks on process IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    private fun fakeSay(body: String): String {
        val script = dir.resolve("say")
        script.writeText("#!/bin/sh\n$body\n")
        script.toFile().setExecutable(true)
        return script.toString()
    }

    private fun catalog(program: String) =
        SayVoiceCatalog(LoginShellProcessRunner(io, Path.of("/bin/sh")), dir, program)

    @Test
    fun `real runner output is parsed`() =
        runTest {
            val log = dir.resolve("args.log")
            val say =
                fakeSay(
                    """
                    for a in "${'$'}@"; do printf '%s\n' "${'$'}a"; done > '$log'
                    printf '%s\n' 'Zosia               pl_PL    # Witaj, nazywam się Zosia.'
                    printf '%s\n' 'Eddy (polski (Polska)) pl_PL    # Witaj'
                    printf '%s\n' 'Paulina             es_419   # Hola'
                    printf '%s\n' 'Bad News            en_US    # Hello'
                    """.trimIndent(),
                )

            val result = catalog(say).voices()

            result shouldBe
                Outcome.Ok(
                    listOf(
                        VoiceOption(VoiceId("Bad News"), "en_US"),
                        VoiceOption(VoiceId("Eddy (polski (Polska))"), "pl_PL"),
                        VoiceOption(VoiceId("Paulina"), "es_419"),
                        VoiceOption(VoiceId("Zosia"), "pl_PL"),
                    ),
                )
            log.readLines() shouldBe listOf("-v", "?")
        }

    @Test
    fun `a failing say gives FAILED`() =
        runTest {
            val say = fakeSay("echo 'secret' >&2; exit 3")

            catalog(say).voices() shouldBe Outcome.Err(VoiceCatalogError.FAILED)
        }
}
