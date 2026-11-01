package dev.quayside.notifications.ui

import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.PopupAction
import dev.quayside.notifications.api.PopupId
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupSettings
import dev.quayside.notifications.api.PopupStyle
import dev.quayside.notifications.api.RecordingNotificationCenter
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The controller joins the popups of the center with the popup settings and forwards actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class PopupControllerTest {
    private val center = RecordingNotificationCenter()
    private val repo = FakeNotificationSettingsRepo()

    @Test
    fun `starts empty with the default position and style`() =
        runTest {
            val controller = PopupController(center, repo, backgroundScope)
            controller.state.value.popups
                .shouldBeEmpty()
            controller.state.value.position shouldBe PopupPosition.BOTTOM_RIGHT
            controller.state.value.style shouldBe PopupStyle.DETAILED
        }

    @Test
    fun `follows the popups of the center`() =
        runTest {
            val controller = PopupController(center, repo, backgroundScope)
            center.show(aPopup("a"), aPopup("b"))
            runCurrent()
            controller.state.value.popups
                .map { it.id.value } shouldBe listOf("a", "b")
        }

    @Test
    fun `follows a change of the popup settings`() =
        runTest {
            val controller = PopupController(center, repo, backgroundScope)
            repo.update {
                it.copy(popup = PopupSettings(position = PopupPosition.TOP_LEFT, style = PopupStyle.COMPACT))
            }
            runCurrent()
            controller.state.value.position shouldBe PopupPosition.TOP_LEFT
            controller.state.value.style shouldBe PopupStyle.COMPACT
        }

    @Test
    fun `an action reaches the center`() =
        runTest {
            val controller = PopupController(center, repo, backgroundScope)
            controller.onAction(PopupId("a"), PopupAction.MUTE)
            runCurrent()
            center.received shouldBe listOf(PopupId("a") to PopupAction.MUTE)
        }

    @Test
    fun `settings other than popup do not matter`() =
        runTest {
            val controller = PopupController(center, repo, backgroundScope)
            val before = controller.state.value
            repo.update { it.copy(voice = !it.voice) }
            runCurrent()
            controller.state.value shouldBe before
        }
}
