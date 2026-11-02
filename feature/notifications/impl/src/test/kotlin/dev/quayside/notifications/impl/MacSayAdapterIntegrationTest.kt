package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.LoginShellProcessRunner
import dev.quayside.notifications.api.VoiceId
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
 * [MacSayAdapter] with the real [LoginShellProcessRunner] and a fake `say` script in a temp directory (docs/12 §4).
 * No real speech: the script only records its arguments.
 */
class MacSayAdapterIntegrationTest {
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

    private fun adapter(program: String) = MacSayAdapter(LoginShellProcessRunner(io, Path.of("/bin/sh")), dir, program)

    private fun utterance(text: String) = Utterance(text, VoiceId("Zosia"), 190, 70)

    @Test
    fun `real runner passes arguments unchanged`() =
        runTest {
            val say = fakeSay("for a in \"\$@\"; do printf '%s\\n' \"\$a\"; done > '$log'")
            val text = "it's \"quoted\" \$HOME  and *"

            val result = adapter(say).speak(utterance(text))

            result shouldBe Outcome.Ok(Unit)
            log.readLines() shouldBe listOf("-v", "Zosia", "-r", "190", "[[volm 0.70]] $text")
        }

    @Test
    fun `two utterances run one after another`() =
        runTest {
            val say = fakeSay("echo \"start \$5\" >> '$log'; sleep 0.2; echo \"end \$5\" >> '$log'")
            val adapter = adapter(say)

            adapter.speak(utterance("one"))
            adapter.speak(utterance("two"))

            log.readLines() shouldBe
                listOf(
                    "start [[volm 0.70]] one",
                    "end [[volm 0.70]] one",
                    "start [[volm 0.70]] two",
                    "end [[volm 0.70]] two",
                )
        }

    @Test
    fun `a missing voice is retried without it`() =
        runTest {
            val say = fakeSay("echo \"\$#\" >> '$log'; if [ \"\$1\" = \"-v\" ]; then exit 1; fi")

            val result = adapter(say).speak(utterance("hi"))

            result shouldBe Outcome.Ok(Unit)
            log.readLines() shouldBe listOf("5", "3")
        }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `cancel kills the fake say`() =
        runTest {
            val pidFile = dir.resolve("self.pid")
            val say = fakeSay("echo \$\$ > '$pidFile'; exec sleep 30")
            val running = async { adapter(say).speak(utterance("long")) }
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
