@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * What the open preview shows over time: loading, rereads every 3 s, failures and a changed selection (FR-K2, AM-411).
 */
internal class SessionPreviewRefreshTest : PreviewTestKit by DefaultPreviewTestKit() {
    @Test
    fun `the preview shows loading until the first read ends`() =
        runTest {
            val fake = world(session("a", 1))
            val gated = GatedOutput(fake)
            val controller = start(fake, gated)
            select(controller, a)
            gated.hold()

            send(controller, MissionControlIntent.TogglePreview)

            controller.preview() shouldBe PreviewUi(a, "name-a", PreviewContent.Loading)

            gated.release()
            runCurrent()

            controller.preview() shouldBe text("a")
        }

    @Test
    fun `an empty output shows empty`() =
        runTest {
            val fake = world(session("a", 1))
            fake.setOutput(a, "  \n")
            val controller = start(fake)
            select(controller, a)

            send(controller, MissionControlIntent.TogglePreview)

            controller.preview() shouldBe PreviewUi(a, "name-a", PreviewContent.Empty)
        }

    @Test
    fun `the open preview reads again every 3 seconds and keeps the old text meanwhile`() =
        runTest {
            val fake = world(session("a", 1))
            val gated = GatedOutput(fake)
            val controller = start(fake, gated)
            select(controller, a)
            send(controller, MissionControlIntent.TogglePreview)
            fake.setOutput(a, "newer")

            pass(2900.milliseconds)
            controller.preview() shouldBe text("a")
            gated.started shouldBe listOf(a)

            gated.hold()
            pass(100.milliseconds)
            gated.started shouldBe listOf(a, a)
            // The second read is still running: the old text stays, no loading flicker.
            controller.preview() shouldBe text("a")

            gated.release()
            runCurrent()
            controller.preview() shouldBe PreviewUi(a, "name-a", PreviewContent.Text("newer"))

            pass(3.seconds)
            gated.started shouldBe listOf(a, a, a)
        }

    @Test
    fun `a failed reread replaces the text`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, a)
            send(controller, MissionControlIntent.TogglePreview)
            fake.failNextOutput(SessionError.Runtime(RuntimeProblem.TIMEOUT))

            pass(3.seconds)

            controller.preview() shouldBe
                PreviewUi(a, "name-a", PreviewContent.Failed(UiText("action.reason.runtime.TIMEOUT")))

            pass(3.seconds)

            controller.preview() shouldBe text("a")
        }

    @Test
    fun `closing stops the rereads`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, a)
            send(controller, MissionControlIntent.TogglePreview)
            send(controller, MissionControlIntent.TogglePreview)

            pass(30.seconds)

            fake.outputReads shouldBe listOf(a)
        }

    @Test
    fun `losing the selected session closes the preview`() =
        runTest {
            val fake = world(session("a", 1, SessionState.Done))
            val controller = start(fake)
            select(controller, a)
            send(controller, MissionControlIntent.TogglePreview)

            fake.remove(a)
            runCurrent()

            controller.preview().shouldBeNull()
            controller.actions.value.previewOpen shouldBe false
            pass(30.seconds)
            fake.outputReads shouldBe listOf(a)
        }

    @Test
    fun `hiding the selected session by the filter closes the preview`() =
        runTest {
            val fake = world(session("a", 1))
            val controller = start(fake)
            select(controller, a)
            send(controller, MissionControlIntent.TogglePreview)

            controller.onEvent(MissionControlEvent.SelectFilter(SessionFilter.FINISHED))
            runCurrent()

            controller.preview().shouldBeNull()
            controller.actions.value.previewOpen shouldBe false
        }

    @Test
    fun `a slow read of the previous session is dropped`() =
        runTest {
            val fake = world(session("a", 1), session("b", 2))
            val gated = GatedOutput(fake)
            val controller = start(fake, gated)
            select(controller, b)
            gated.hold()
            send(controller, MissionControlIntent.TogglePreview)
            controller.preview() shouldBe PreviewUi(b, "name-b", PreviewContent.Loading)

            send(controller, MissionControlIntent.NextSession)
            controller.preview() shouldBe PreviewUi(a, "name-a", PreviewContent.Loading)
            gated.release()
            runCurrent()

            controller.preview() shouldBe text("a")
            gated.started shouldBe listOf(b, a)
        }
}
