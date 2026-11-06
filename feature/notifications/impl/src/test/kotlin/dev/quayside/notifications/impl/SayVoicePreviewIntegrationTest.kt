package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.LoginShellProcessRunner
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceSample
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds

/**
 * [SayVoicePreview] with the real [LoginShellProcessRunner] and a fake `say` script in a temp directory (docs/12 §4).
 * No real speech: the script only records its arguments or sleeps.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class SayVoicePreviewIntegrationTest {
    @TempDir
    lateinit var dir: Path

    // Real threads: the runner blocks on process IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    private val log get() = dir.resolve("args.log")

    private fun fakeSay(body: String): String {
        val script = dir.resolve("say")
        script.writeText("#!/bin/sh\n$body\n")
        script.toFile().setExecutable(true)
        return script.toString()
    }

    private fun preview(program: String) =
        SayVoicePreview(LoginShellProcessRunner(io, Path.of("/bin/sh")), dir, program)

    @Test
    fun `real runner passes voice, rate and volume`() =
        runTest {
            val say = fakeSay("for a in \"\$@\"; do printf '%s\\n' \"\$a\"; done > '$log'")
            val text = "Hello #1 \"here\" \$HOME"

            val result = preview(say).play(VoiceSample(VoiceId("Zosia"), text, rate = 250, volume = 40))

            result shouldBe Outcome.Ok(Unit)
            log.readLines() shouldBe listOf("-v", "Zosia", "-r", "250", "[[volm 0.40]] $text")
        }

    @Test
    fun `cancelling kills the say script`() =
        runTest {
            val pidFile = dir.resolve("self.pid")
            val say = fakeSay("echo \$\$ > '$pidFile'; exec sleep 30")
            val running = async { preview(say).play(VoiceSample(null, "long", rate = 190, volume = 70)) }
            withContext(io) {
                while (!pidFile.exists() || pidFile.readText().isBlank()) delay(10.milliseconds)
            }
            val pid = pidFile.readText().trim().toLong()

            running.cancelAndJoin()

            withContext(io) {
                var tries = 0
                while (ProcessHandle.of(pid).map { it.isAlive }.orElse(false) && tries++ < ALIVE_CHECKS) {
                    delay(50.milliseconds)
                }
            }
            ProcessHandle.of(pid).map { it.isAlive }.orElse(false) shouldBe false
        }

    private companion object {
        const val ALIVE_CHECKS = 100
    }
}
