@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.FakeSoundLibrary
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.SoundId
import dev.quayside.notifications.api.SoundLibraryError
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** [SoundPreviewController]: loading the list once and one preview at a time (FR-N1). */
class SoundPreviewControllerTest {
    private val library = FakeSoundLibrary()
    private val glass = SoundId("Glass")

    private fun TestScope.controller() = SoundPreviewController(library, backgroundScope)

    @Test
    fun `load fills the list`() =
        runTest {
            val controller = controller()

            controller.load()
            runCurrent()

            controller.state.value.sounds shouldBe FakeSoundLibrary.DEFAULT_SOUNDS.sortedBy { it.name.lowercase() }
        }

    @Test
    fun `a loaded list is not asked for again`() =
        runTest {
            val controller = controller()
            controller.load()
            runCurrent()

            controller.load()
            runCurrent()

            library.listCalls shouldBe 1
        }

    @Test
    fun `a failed list is tried again at the next load`() =
        runTest {
            library.list = Outcome.Err(SoundLibraryError.UNAVAILABLE)
            val controller = controller()
            controller.load()
            runCurrent()
            controller.state.value.listFailed shouldBe true
            controller.state.value.sounds shouldBe null

            library.list = Outcome.Ok(listOf(glass))
            controller.load()
            runCurrent()

            controller.state.value.listFailed shouldBe false
            controller.state.value.sounds shouldBe listOf(glass)
        }

    @Test
    fun `a load that runs is not started twice`() =
        runTest {
            val controller = controller()

            controller.load()
            controller.load()
            runCurrent()

            library.listCalls shouldBe 1
        }

    @Test
    fun `toggle plays the sound at the volume and ends`() =
        runTest {
            val controller = controller()

            controller.toggle(NotificationKind.FAILED, glass, 30)
            controller.state.value.previewing shouldBe NotificationKind.FAILED
            runCurrent()

            library.played shouldBe listOf(glass to 30)
            controller.state.value.previewing shouldBe null
            controller.state.value.previewFailed shouldBe false
        }

    @Test
    fun `a failed preview is shown and the next one clears it`() =
        runTest {
            library.result = Outcome.Err(SoundLibraryError.FAILED)
            val controller = controller()
            controller.toggle(NotificationKind.FAILED, glass, 30)
            runCurrent()
            controller.state.value.previewFailed shouldBe true

            library.result = Outcome.Ok(Unit)
            library.gate = CompletableDeferred()
            controller.toggle(NotificationKind.FAILED, glass, 30)

            controller.state.value.previewFailed shouldBe false
        }

    @Test
    fun `toggle of the playing kind stops it`() =
        runTest {
            library.gate = CompletableDeferred()
            val controller = controller()
            controller.toggle(NotificationKind.FAILED, glass, 30)
            runCurrent()

            controller.toggle(NotificationKind.FAILED, glass, 30)
            runCurrent()

            library.cancelled shouldBe 1
            controller.state.value.previewing shouldBe null
        }

    @Test
    fun `toggle of another kind replaces the preview`() =
        runTest {
            library.gate = CompletableDeferred()
            val controller = controller()
            controller.toggle(NotificationKind.FAILED, glass, 30)
            runCurrent()

            controller.toggle(NotificationKind.TURN_ENDED, SoundId("Hero"), 50)
            runCurrent()

            library.cancelled shouldBe 1
            controller.state.value.previewing shouldBe NotificationKind.TURN_ENDED
        }

    @Test
    fun `stop cancels the preview and drops its result`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            library.gate = gate
            library.result = Outcome.Err(SoundLibraryError.FAILED)
            val controller = controller()
            controller.toggle(NotificationKind.FAILED, glass, 30)
            runCurrent()

            controller.stop()
            gate.complete(Unit)
            runCurrent()

            library.cancelled shouldBe 1
            controller.state.value.previewing shouldBe null
            controller.state.value.previewFailed shouldBe false
        }
}
