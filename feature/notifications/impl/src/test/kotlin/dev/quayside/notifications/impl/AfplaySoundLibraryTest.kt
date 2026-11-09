@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.notifications.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.process.FakeProcessRunner
import dev.quayside.core.process.ProcessError
import dev.quayside.core.process.ProcessResult
import dev.quayside.notifications.api.SoundId
import dev.quayside.notifications.api.SoundLibrary
import dev.quayside.notifications.api.SoundLibraryContract
import dev.quayside.notifications.api.SoundLibraryError
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/** [AfplaySoundLibrary] passes the [SoundLibraryContract] on a temp sounds directory and a fake `afplay`. */
class AfplaySoundLibraryTest : SoundLibraryContract() {
    @TempDir
    lateinit var dir: Path

    private val dispatcher = UnconfinedTestDispatcher()

    private fun sounds(vararg files: String): Path {
        val sounds = dir.resolve("sounds").createDirectories()
        files.forEach { sounds.resolve(it).writeText("x") }
        return sounds
    }

    private fun library(
        runner: FakeProcessRunner,
        soundsDir: Path,
    ) = AfplaySoundLibrary(runner, dir, dispatcher, soundsDir)

    override fun working(): SoundLibrary =
        library(
            FakeProcessRunner { cmd ->
                if (cmd.argv.last().endsWith("Missing.aiff")) {
                    Outcome.Ok(ProcessResult(1, "", ""))
                } else {
                    Outcome.Ok(ProcessResult(0, "", ""))
                }
            },
            sounds("hero.aiff", "Glass.aiff", "Basso.aiff", "notes.txt"),
        )

    override fun failing(): SoundLibrary =
        library(
            FakeProcessRunner { Outcome.Err(ProcessError.NotFound("/usr/bin/afplay")) },
            dir.resolve("absent"),
        )

    @Test
    fun `files that are not valid names are left out`() =
        runTest {
            val library = library(FakeProcessRunner(), sounds("Glass.aiff", "-v.aiff", ".DS_Store", "Ping.AIFF"))

            library.sounds() shouldBe Outcome.Ok(listOf(SoundId("Glass"), SoundId("Ping")))
        }

    @Test
    fun `preview runs afplay without a fallback sound`() =
        runTest {
            val runner = FakeProcessRunner { Outcome.Ok(ProcessResult(1, "", "")) }

            library(runner, sounds()).play(SoundId("Basso"), 20) shouldBe Outcome.Err(SoundLibraryError.FAILED)

            runner.commands.size shouldBe 1
            runner.commands
                .single()
                .argv
                .subList(1, 3) shouldBe listOf("-v", "0.20")
        }
}
