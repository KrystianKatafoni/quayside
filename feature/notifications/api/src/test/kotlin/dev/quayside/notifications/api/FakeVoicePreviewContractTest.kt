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

/** The fake passes the same contract as the `say` adapter (ENG-T2). */
class FakeVoicePreviewContractTest : VoicePreviewContract() {
    private var last = FakeVoicePreview()

    override fun working(): VoicePreview = FakeVoicePreview().also { last = it }

    override fun failing(): VoicePreview = FakeVoicePreview.failing().also { last = it }

    override fun started(): Int = last.played.size

    @Test
    fun `fake records the samples it played`() =
        runTest {
            val fake = FakeVoicePreview()
            val sample = VoiceSample(voice = null, text = "Hi", rate = 250, volume = 40)

            fake.play(sample)

            fake.played shouldBe listOf(sample)
        }

    @Test
    fun `fake waits for its gate and counts a cancelled play`() =
        runTest {
            val fake = FakeVoicePreview().apply { gate = CompletableDeferred() }
            val job = launch { fake.play(VoiceSample(null, "Hi", 190, 70)) }
            runCurrent()
            job.isActive shouldBe true

            job.cancel()
            runCurrent()

            fake.cancelled shouldBe 1
        }

    @Test
    fun `fake gives the scripted result once the gate opens`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val fake = FakeVoicePreview(Outcome.Err(VoicePreviewError.UNAVAILABLE)).apply { this.gate = gate }
            var result: Outcome<Unit, VoicePreviewError>? = null
            launch { result = fake.play(VoiceSample(null, "Hi", 190, 70)) }
            runCurrent()
            result shouldBe null

            gate.complete(Unit)
            runCurrent()

            result shouldBe Outcome.Err(VoicePreviewError.UNAVAILABLE)
        }
}
