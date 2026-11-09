@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.FakeVoiceCatalog
import dev.quayside.notifications.api.FakeVoicePreview
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.NotificationSettingsError
import dev.quayside.notifications.api.SpeechSettings
import dev.quayside.notifications.api.VoiceId
import dev.quayside.notifications.api.VoiceOption
import dev.quayside.notifications.api.VoicePreview
import dev.quayside.notifications.api.VoicePreviewError
import dev.quayside.notifications.api.VoiceSample
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

/** The sample of the drafted voice in the notification settings controller (FR-N4, plan AM-813). */
class NotificationSettingsPreviewTest {
    private val ewa = VoiceOption(VoiceId("Ewa"), "pl_PL", "Witaj, jestem Ewa.")
    private val alex = VoiceOption(VoiceId("Alex"), "en_US", "Hello, I am Alex.")

    /** A preview that ignores cancelling: its result arrives late, as a real one may do right after a stop. */
    private class StubbornPreview : VoicePreview {
        val gates = mutableListOf<CompletableDeferred<Outcome<Unit, VoicePreviewError>>>()

        override suspend fun play(sample: VoiceSample): Outcome<Unit, VoicePreviewError> {
            val gate = CompletableDeferred<Outcome<Unit, VoicePreviewError>>()
            gates += gate
            return withContext(NonCancellable) { gate.await() }
        }
    }

    private fun scripted(settings: NotificationSettings = NotificationSettings()) =
        ScriptedRepo(FakeNotificationSettingsRepo(settings))

    private fun TestScope.controllerOver(
        repo: ScriptedRepo,
        preview: VoicePreview,
    ) = NotificationSettingsController(
        repo,
        FakeVoiceCatalog(listOf(ewa, alex)),
        preview,
        MutableStateFlow(emptyList()),
        backgroundScope,
    )

    /** An opened controller with the voices loaded and Ewa drafted. */
    private fun TestScope.opened(
        preview: VoicePreview,
        repo: ScriptedRepo = scripted(),
    ): NotificationSettingsController {
        val controller = controllerOver(repo, preview)
        act { controller.open() }
        act { controller.edit { it.copy(speech = it.speech.copy(voice = VoiceId("Ewa"))) } }
        return controller
    }

    private fun NotificationSettingsController.dialog() = state.value.shouldNotBeNull()

    private fun gated(result: Outcome<Unit, VoicePreviewError> = Outcome.Ok(Unit)) =
        FakeVoicePreview(result).apply { gate = CompletableDeferred() }

    @Test
    fun `toggle plays the drafted voice`() =
        runTest {
            val preview = gated()
            val controller = opened(preview)
            act {
                controller.edit { it.copy(speech = it.speech.copy(rate = "250", volume = "40")) }
            }

            act { controller.togglePreview() }

            preview.played shouldBe listOf(VoiceSample(VoiceId("Ewa"), "Witaj, jestem Ewa.", rate = 250, volume = 40))
            controller.dialog().previewing shouldBe true
            controller.dialog().previewFailed shouldBe false
        }

    @Test
    fun `an invalid draft rate plays with the saved one`() =
        runTest {
            val preview = gated()
            val controller = opened(preview, scripted(NotificationSettings(speech = SpeechSettings(rate = 220))))
            act { controller.edit { it.copy(speech = it.speech.copy(rate = "abc")) } }

            act { controller.togglePreview() }

            preview.played.single().rate shouldBe 220
        }

    @Test
    fun `the end of the sample clears previewing`() =
        runTest {
            val preview = gated()
            val controller = opened(preview)
            act { controller.togglePreview() }

            act { preview.gate?.complete(Unit) }

            controller.dialog().previewing shouldBe false
            controller.dialog().previewFailed shouldBe false
        }

    @Test
    fun `second toggle stops and clears previewing`() =
        runTest {
            val preview = gated()
            val controller = opened(preview)
            act { controller.togglePreview() }

            act { controller.togglePreview() }

            controller.dialog().previewing shouldBe false
            preview.cancelled shouldBe 1
            preview.played.size shouldBe 1
        }

    @Test
    fun `toggle after a stop plays again`() =
        runTest {
            val preview = gated()
            val controller = opened(preview)
            act { controller.togglePreview() }
            act { controller.togglePreview() }

            act { controller.togglePreview() }

            controller.dialog().previewing shouldBe true
            preview.played.size shouldBe 2
        }

    @Test
    fun `open while a sample plays stops it`() =
        runTest {
            val preview = gated()
            val controller = opened(preview)
            act { controller.togglePreview() }

            act { controller.open() }

            preview.cancelled shouldBe 1
            controller.dialog().previewing shouldBe false
        }

    @Test
    fun `cancel stops the sample`() =
        runTest {
            val preview = gated()
            val controller = opened(preview)
            act { controller.togglePreview() }

            act { controller.cancel() }

            preview.cancelled shouldBe 1
            controller.state.value.shouldBeNull()
            act { controller.open() }
            controller.dialog().previewing shouldBe false
        }

    @Test
    fun `successful save stops the sample`() =
        runTest {
            val preview = gated()
            val controller = opened(preview)
            act { controller.togglePreview() }

            act { controller.save() }

            controller.state.value.shouldBeNull()
            preview.cancelled shouldBe 1
        }

    @Test
    fun `a refused save keeps the sample playing`() =
        runTest {
            val preview = gated()
            val controller = opened(preview)
            act { controller.togglePreview() }
            act { controller.edit { it.copy(speech = it.speech.copy(rate = "999")) } }

            act { controller.save() }

            controller.dialog().invalid.shouldNotBeNull()
            controller.dialog().previewing shouldBe true
            preview.cancelled shouldBe 0
        }

    @Test
    fun `a failed write keeps the sample playing`() =
        runTest {
            val preview = gated()
            val repo = scripted().apply { failure = NotificationSettingsError.WRITE_FAILED }
            val controller = opened(preview, repo)
            act { controller.togglePreview() }

            act { controller.save() }

            controller.dialog().saveError shouldBe NotificationSettingsError.WRITE_FAILED
            controller.dialog().previewing shouldBe true
            preview.cancelled shouldBe 0
        }

    @Test
    fun `open after cancel drops a late failure`() =
        runTest {
            val preview = StubbornPreview()
            val controller = opened(preview)
            act { controller.togglePreview() }
            act { controller.cancel() }
            act { controller.open() }

            act { preview.gates.single().complete(Outcome.Err(VoicePreviewError.FAILED)) }

            controller.dialog().previewFailed shouldBe false
            controller.dialog().previewing shouldBe false
        }

    @Test
    fun `open after cancel drops a late result of the old sample while a new one plays`() =
        runTest {
            val preview = StubbornPreview()
            val controller = opened(preview)
            act { controller.togglePreview() }
            act { controller.cancel() }
            act { controller.open() }
            act { controller.togglePreview() }

            act { preview.gates.first().complete(Outcome.Ok(Unit)) }

            controller.dialog().previewing shouldBe true
            act { preview.gates.last().complete(Outcome.Err(VoicePreviewError.FAILED)) }
            controller.dialog().previewing shouldBe false
            controller.dialog().previewFailed shouldBe true
        }

    @Test
    fun `a result after a stop is dropped`() =
        runTest {
            val preview = StubbornPreview()
            val controller = opened(preview)
            act { controller.togglePreview() }
            act { controller.togglePreview() }

            act { preview.gates.single().complete(Outcome.Err(VoicePreviewError.FAILED)) }

            controller.dialog().previewFailed shouldBe false
            controller.dialog().previewing shouldBe false
        }

    @Test
    fun `failure sets previewFailed`() =
        runTest {
            val preview = FakeVoicePreview.failing()
            val controller = opened(preview)

            act { controller.togglePreview() }

            controller.dialog().previewing shouldBe false
            controller.dialog().previewFailed shouldBe true
        }

    @Test
    fun `edit clears previewFailed`() =
        runTest {
            val controller = opened(FakeVoicePreview.failing())
            act { controller.togglePreview() }
            controller.dialog().previewFailed shouldBe true

            act { controller.edit { it.copy(speech = it.speech.copy(rate = "200")) } }

            controller.dialog().previewFailed shouldBe false
        }

    @Test
    fun `a new sample clears the earlier failure`() =
        runTest {
            val preview = FakeVoicePreview.failing()
            val controller = opened(preview)
            act { controller.togglePreview() }
            preview.result = Outcome.Ok(Unit)
            preview.gate = CompletableDeferred()

            act { controller.togglePreview() }

            controller.dialog().previewFailed shouldBe false
            controller.dialog().previewing shouldBe true
        }

    @Test
    fun `open starts without previewing or failure`() =
        runTest {
            val controller = opened(FakeVoicePreview.failing())
            act { controller.togglePreview() }
            act { controller.cancel() }

            act { controller.open() }

            controller.dialog().previewing shouldBe false
            controller.dialog().previewFailed shouldBe false
        }

    @Test
    fun `toggle without dialog does nothing`() =
        runTest {
            val preview = gated()
            val controller = controllerOver(scripted(), preview)

            act { controller.togglePreview() }

            preview.played shouldBe emptyList()
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `the system voice plays the fixed text`() =
        runTest {
            val preview = gated()
            val controller = opened(preview)
            act { controller.edit { it.copy(speech = it.speech.copy(voice = null)) } }

            act { controller.togglePreview() }

            preview.played.single() shouldBe
                VoiceSample(voice = null, text = "Tak brzmi głos powiadomień.", rate = 190, volume = 70)
        }
}
