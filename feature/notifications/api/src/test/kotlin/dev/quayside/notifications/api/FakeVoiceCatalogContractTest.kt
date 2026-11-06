package dev.quayside.notifications.api

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The fake passes the same contract as the `say` adapter (ENG-T2). */
class FakeVoiceCatalogContractTest : VoiceCatalogContract() {
    override fun catalogOf(voices: List<VoiceOption>): VoiceCatalog = FakeVoiceCatalog(voices)

    override fun failing(): VoiceCatalog = FakeVoiceCatalog.failing()

    @Test
    fun `fake counts its asks`() =
        runTest {
            val fake = FakeVoiceCatalog(emptyList())

            fake.voices()
            fake.voices()

            fake.calls shouldBe 2
        }
}
