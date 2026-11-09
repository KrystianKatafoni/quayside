@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.notifications.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The fake passes the same contract as the `afplay` adapter (ENG-T2). */
class FakeSoundLibraryContractTest : SoundLibraryContract() {
    override fun working(): SoundLibrary =
        FakeSoundLibrary(Outcome.Ok(listOf(SoundId("hero"), SoundId("Glass"), SoundId("Basso"))))

    override fun failing(): SoundLibrary = FakeSoundLibrary(list = Outcome.Err(SoundLibraryError.UNAVAILABLE))

    @Test
    fun `fake records the plays it started`() =
        runTest {
            val fake = FakeSoundLibrary()

            fake.play(SoundId("Glass"), 40)

            fake.played shouldBe listOf(SoundId("Glass") to 40)
        }

    @Test
    fun `fake gives the scripted result of an installed sound`() =
        runTest {
            val fake = FakeSoundLibrary(result = Outcome.Err(SoundLibraryError.UNAVAILABLE))

            fake.play(SoundId("Glass"), 40) shouldBe Outcome.Err(SoundLibraryError.UNAVAILABLE)
        }

    @Test
    fun `fake waits for its gate and counts a cancelled play`() =
        runTest {
            val fake = FakeSoundLibrary().apply { gate = CompletableDeferred() }
            val job = launch { fake.play(SoundId("Glass"), 40) }
            runCurrent()
            job.isActive shouldBe true

            job.cancel()
            runCurrent()

            fake.cancelled shouldBe 1
        }
}
