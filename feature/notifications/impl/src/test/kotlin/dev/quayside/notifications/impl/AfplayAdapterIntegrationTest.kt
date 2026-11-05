package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.LoginShellProcessRunner
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
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds

/**
 * [AfplayAdapter] with the real [LoginShellProcessRunner] and a fake `afplay` script in a temp directory (docs/12 §4).
 * No real sound: the script only records its arguments.
 */
class AfplayAdapterIntegrationTest {
    @TempDir
    lateinit var dir: Path

    // Real threads: the runner blocks on process IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    private val log get() = dir.resolve("args.log")

    /** A sounds directory with a space in its name; [names] are the files that exist in it. */
    private fun soundsDir(vararg names: String): Path {
        val sounds = dir.resolve("my sounds").createDirectories()
        names.forEach { sounds.resolve("$it.aiff").writeText("x") }
        return sounds
    }

    private fun fakeAfplay(body: String): String {
        val script = dir.resolve("afplay")
        script.writeText("#!/bin/sh\n$body\n")
        script.toFile().setExecutable(true)
        return script.toString()
    }

    private fun adapter(
        program: String,
        sounds: Path,
    ) = AfplayAdapter(LoginShellProcessRunner(io, Path.of("/bin/sh")), dir, sounds, program)

    @Test
    fun `real runner passes the sound path unchanged`() =
        runTest {
            val sounds = soundsDir("Basso")
            val afplay = fakeAfplay("for a in \"\$@\"; do printf '%s\\n' \"\$a\"; done > '$log'")

            val result = adapter(afplay, sounds).play(SystemSound.BASSO)

            result shouldBe Outcome.Ok(Unit)
            log.readLines() shouldBe listOf("${sounds.resolve("Basso.aiff")}")
        }

    @Test
    fun `missing file exit code triggers the fallback`() =
        runTest {
            val sounds = soundsDir("Glass")
            val afplay = fakeAfplay("echo \"\$1\" >> '$log'; [ -f \"\$1\" ] || exit 1")

            val result = adapter(afplay, sounds).play(SystemSound.HERO)

            result shouldBe Outcome.Ok(Unit)
            log.readLines() shouldBe listOf("${sounds.resolve("Hero.aiff")}", "${sounds.resolve("Glass.aiff")}")
        }

    @Test
    fun `a missing fallback file ends as Failed`() =
        runTest {
            val sounds = soundsDir()
            val afplay = fakeAfplay("[ -f \"\$1\" ] || exit 1")

            adapter(afplay, sounds).play(SystemSound.HERO) shouldBe Outcome.Err(SoundError.Failed(1))
        }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `cancel kills the fake afplay`() =
        runTest {
            val pidFile = dir.resolve("self.pid")
            val afplay = fakeAfplay("echo \$\$ > '$pidFile'; exec sleep 30")
            val running = async { adapter(afplay, soundsDir("Glass")).play(SystemSound.GLASS) }
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
