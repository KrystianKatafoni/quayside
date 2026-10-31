@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * TogglePreview, closing, dialogs and `actions.canPreview` / `previewOpen` (FR-K2, AM-411).
 */
internal class SessionPreviewControllerTest : PreviewTestKit by DefaultPreviewTestKit() {
    @Test
    fun `toggle without a selection opens nothing`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)

            send(controller, MissionControlIntent.TogglePreview)
            pass(10.seconds)

            controller.preview().shouldBeNull()
            controller.actions.value.previewOpen shouldBe false
            fake.outputReads.shouldBeEmpty()
        }

    @Test
    fun `toggle opens the preview of the selected session`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            val controller = start(fake)
            select(controller, a)

            send(controller, MissionControlIntent.TogglePreview)

            controller.preview() shouldBe text("a")
            controller.actions.value.previewOpen shouldBe true
            fake.outputReads shouldBe listOf(a)
            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `toggle again closes it`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, a)
            send(controller, MissionControlIntent.TogglePreview)

            send(controller, MissionControlIntent.TogglePreview)

            controller.preview().shouldBeNull()
            controller.actions.value.previewOpen shouldBe false
            controller.actions.value.canPreview shouldBe true
        }

    @Test
    fun `the close button toggles the preview too`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, a)
            send(controller, MissionControlIntent.TogglePreview)

            controller.onEvent(MissionControlEvent.TogglePreview)
            runCurrent()

            controller.preview().shouldBeNull()
        }

    @Test
    fun `next session loads the output of the new session`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            val controller = start(fake)
            select(controller, b)
            send(controller, MissionControlIntent.TogglePreview)
            controller.preview() shouldBe text("b")

            send(controller, MissionControlIntent.NextSession)

            controller.preview() shouldBe text("a")
            fake.outputReads shouldBe listOf(b, a)
        }

    @Test
    fun `the toggle event is ignored while the dialog is open`() =
        runTest {
            val fake = world(session("a", 1, SessionState.Done))
            val controller = start(fake)
            select(controller, a)
            send(controller, MissionControlIntent.RemoveSelected)

            controller.onEvent(MissionControlEvent.TogglePreview)
            runCurrent()

            controller.preview().shouldBeNull()
            fake.outputReads.shouldBeEmpty()

            send(controller, MissionControlIntent.TogglePreview)
            controller.onEvent(MissionControlEvent.Dismiss)
            runCurrent()
            controller.onEvent(MissionControlEvent.TogglePreview)
            runCurrent()
            controller.preview() shouldBe text("a")
            controller.onEvent(MissionControlEvent.TogglePreview)
            runCurrent()
            controller.preview().shouldBeNull()
        }

    @Test
    fun `close and open in one step leaves an open preview with content`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, a)
            send(controller, MissionControlIntent.TogglePreview)

            // No runCurrent between the toggles: the key goes a -> null -> a within one dispatcher step.
            controller.onIntent(MissionControlIntent.TogglePreview)
            controller.onIntent(MissionControlIntent.TogglePreview)
            runCurrent()

            controller.actions.value.previewOpen shouldBe true
            controller.preview() shouldBe text("a")
        }

    @Test
    fun `toggle is ignored while the dialog is open`() =
        runTest {
            val fake = world(session("a", 1, SessionState.Done))
            val controller = start(fake)
            select(controller, a)
            send(controller, MissionControlIntent.RemoveSelected)
            controller.state.value.dialog
                .shouldBeInstanceOf<SessionDialog.ConfirmRemove>()

            send(controller, MissionControlIntent.TogglePreview)

            controller.preview().shouldBeNull()
            fake.outputReads.shouldBeEmpty()
        }

    @Test
    fun `toggle does not close an open preview while the dialog is open`() =
        runTest {
            val fake = world(session("a", 1, SessionState.Done))
            val controller = start(fake)
            select(controller, a)
            send(controller, MissionControlIntent.TogglePreview)
            send(controller, MissionControlIntent.RemoveSelected)

            send(controller, MissionControlIntent.TogglePreview)

            controller.preview() shouldBe text("a")
        }

    @Test
    fun `actions report canPreview and previewOpen`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2, SessionState.Done))
            val controller = start(fake)
            controller.actions.value.canPreview shouldBe false
            controller.actions.value.previewOpen shouldBe false

            select(controller, a)
            controller.actions.value.canPreview shouldBe true
            controller.actions.value.previewOpen shouldBe false

            send(controller, MissionControlIntent.TogglePreview)
            controller.actions.value.canPreview shouldBe true
            controller.actions.value.previewOpen shouldBe true

            send(controller, MissionControlIntent.TogglePreview)
            controller.onEvent(MissionControlEvent.SelectFilter(SessionFilter.FINISHED))
            runCurrent()
            controller.actions.value.canPreview shouldBe false

            select(controller, b)
            controller.actions.value.canPreview shouldBe true
            send(controller, MissionControlIntent.RemoveSelected)
            controller.actions.value.canPreview shouldBe false
            controller.actions.value.dialogOpen shouldBe true
        }

    @Test
    fun `canPreview stays on while an action runs`() =
        runTest {
            val fake = world(session("a", 1))
            val gated = GatedCommands(fake)
            val controller = start(fake, commands = gated)
            select(controller, a)

            send(controller, MissionControlIntent.StopSelected)

            controller.actions.value shouldBe
                SessionActionsState(canStop = false, canRespawn = false, canPreview = true, canAttach = true)
            send(controller, MissionControlIntent.TogglePreview)
            controller.preview() shouldBe text("a")
            controller.actions.value.canPreview shouldBe true
        }

    @Test
    fun `preview logs nothing`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            val controller = start(fake)
            select(controller, a)
            send(controller, MissionControlIntent.TogglePreview)
            fake.failNextOutput(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
            pass(3.seconds)
            send(controller, MissionControlIntent.NextSession)
            send(controller, MissionControlIntent.TogglePreview)

            log.entries.shouldBeEmpty()
        }
}
