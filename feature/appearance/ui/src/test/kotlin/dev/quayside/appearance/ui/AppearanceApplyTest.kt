@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceError
import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.UiScale
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Applying, closing and reopening the Appearance dialog over fakes (FR-A3, FR-A4, plan AM-803 part A). */
class AppearanceApplyTest {
    @Test
    fun `apply updates appearance and terminal then closes`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            send(
                controller,
                AppearanceIntent.SelectScheme(SUNSET),
                AppearanceIntent.SelectTypography(MONO),
                AppearanceIntent.SelectScale(UiScale.S),
                AppearanceIntent.SelectDensity(ListDensity.COMFORTABLE),
                AppearanceIntent.SelectTerminalFont("JetBrains Mono"),
                AppearanceIntent.StepTerminalSize(1),
                AppearanceIntent.StepTerminalSize(1),
            )

            send(controller, AppearanceIntent.Apply)

            controller.state.value.shouldBeNull()
            world.fake.settings.value shouldBe
                AppearanceSettings(SUNSET, MONO, UiScale.S, ListDensity.COMFORTABLE)
            world.terminal.settings.value.fontFamily shouldBe "JetBrains Mono"
            world.terminal.settings.value.fontSize shouldBe 19
        }

    @Test
    fun `apply sends null font for from-typography`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            world.terminal.settings.value.fontFamily shouldBe "Menlo"
            send(controller, AppearanceIntent.SelectTerminalFont(null))

            send(controller, AppearanceIntent.Apply)

            world.terminal.settings.value.fontFamily
                .shouldBeNull()
            world.terminal.saves shouldBe 1
        }

    @Test
    fun `apply keeps the terminal settings the dialog does not show`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)

            send(controller, AppearanceIntent.Apply)

            world.terminal.settings.value shouldBe SAVED_TERMINAL
        }

    @Test
    fun `apply error keeps the dialog and the draft`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            send(controller, AppearanceIntent.SelectScheme(SUNSET), AppearanceIntent.StepTerminalSize(1))
            val draft = controller.draft()
            world.fake.failure = AppearanceError.UnknownScheme(SUNSET)

            send(controller, AppearanceIntent.Apply)

            val state = controller.state.value.shouldNotBeNull()
            state.error shouldBe ApplyError.APPEARANCE
            state.busy shouldBe false
            state.draft shouldBe draft
            world.fake.settings.value shouldBe SAVED
            world.terminal.saves shouldBe 0
        }

    @Test
    fun `terminal error after appearance ok shows the terminal error`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            send(controller, AppearanceIntent.SelectScheme(SUNSET))
            world.terminal.failure = true

            send(controller, AppearanceIntent.Apply)

            val state = controller.state.value.shouldNotBeNull()
            state.error shouldBe ApplyError.TERMINAL
            state.busy shouldBe false
            world.fake.settings.value.scheme shouldBe SUNSET
            world.terminal.settings.value shouldBe SAVED_TERMINAL
        }

    @Test
    fun `busy is set while the change is being applied`() =
        runTest {
            val world = DialogWorld(hold = true)
            val controller = opened(world)

            send(controller, AppearanceIntent.Apply)

            controller.state.value
                .shouldNotBeNull()
                .busy shouldBe true
            world.appearance.release.complete(Unit)
            runCurrent()
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `second apply while busy is ignored`() =
        runTest {
            val world = DialogWorld(hold = true)
            val controller = opened(world)

            send(controller, AppearanceIntent.Apply, AppearanceIntent.Apply)
            world.appearance.release.complete(Unit)
            runCurrent()

            world.appearance.calls shouldBe 1
            world.terminal.saves shouldBe 1
        }

    @Test
    fun `edits while busy are ignored`() =
        runTest {
            val world = DialogWorld(hold = true)
            val controller = opened(world)
            send(controller, AppearanceIntent.Apply)

            send(
                controller,
                AppearanceIntent.SelectScheme(SUNSET),
                AppearanceIntent.StepTerminalSize(1),
                AppearanceIntent.SelectTerminalFont(null),
            )

            controller.draft() shouldBe AppearanceDraft(SAVED, "Menlo", 17)
        }

    @Test
    fun `after a failed apply the next apply runs again`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            world.fake.failure = AppearanceError.UnknownScheme(SUNSET)
            send(controller, AppearanceIntent.Apply)
            controller.state.value
                .shouldNotBeNull()
                .error shouldBe ApplyError.APPEARANCE

            world.fake.failure = null
            send(controller, AppearanceIntent.Apply)

            world.appearance.calls shouldBe 2
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `after a failed terminal write the next apply runs again`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            world.terminal.failure = true
            send(controller, AppearanceIntent.Apply)
            controller.state.value
                .shouldNotBeNull()
                .error shouldBe ApplyError.TERMINAL

            world.terminal.failure = false
            send(controller, AppearanceIntent.Apply)

            world.appearance.calls shouldBe 2
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `result after close is dropped on reopen`() =
        runTest {
            // Given an apply that is still running when the user closes and opens the dialog again
            val world = DialogWorld(hold = true)
            val controller = opened(world)
            send(controller, AppearanceIntent.Apply)
            controller.close()
            controller.open()

            // When the old apply ends Ok
            world.appearance.release.complete(Unit)
            runCurrent()

            // Then the new dialog is untouched: still open, not busy, no error
            val state = controller.state.value.shouldNotBeNull()
            state.busy shouldBe false
            state.error.shouldBeNull()
            state.draft shouldBe AppearanceDraft(world.fake.settings.value, "Menlo", 17)
        }

    @Test
    fun `failed result after close is dropped on reopen`() =
        runTest {
            val world = DialogWorld(hold = true)
            world.fake.failure = AppearanceError.UnknownScheme(SUNSET)
            val controller = opened(world)
            send(controller, AppearanceIntent.Apply)
            controller.close()
            controller.open()

            world.appearance.release.complete(Unit)
            runCurrent()

            val state = controller.state.value.shouldNotBeNull()
            state.error.shouldBeNull()
            state.busy shouldBe false
        }

    @Test
    fun `apply works again after close during a running apply`() =
        runTest {
            // The reopened dialog is not blocked by the busy flag of the apply that was running at close
            val world = DialogWorld(hold = true)
            val controller = opened(world)
            send(controller, AppearanceIntent.Apply)
            controller.close()
            controller.open()
            world.appearance.release.complete(Unit)
            runCurrent()

            send(controller, AppearanceIntent.Apply)

            controller.state.value.shouldBeNull()
            world.appearance.calls shouldBe 2
        }

    @Test
    fun `close discards the draft`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            send(controller, AppearanceIntent.SelectScheme(SUNSET), AppearanceIntent.StepTerminalSize(1))

            controller.close()
            runCurrent()
            controller.state.value.shouldBeNull()
            controller.open()

            controller.draft() shouldBe AppearanceDraft(SAVED, "Menlo", 17)
            world.fake.settings.value shouldBe SAVED
        }

    @Test
    fun `intents do nothing while the dialog is closed`() =
        runTest {
            val world = DialogWorld()
            val controller = controllerOf(world)

            send(controller, AppearanceIntent.SelectScheme(SUNSET), AppearanceIntent.Apply)

            controller.state.value.shouldBeNull()
            world.appearance.calls shouldBe 0
            world.terminal.saves shouldBe 0
        }
}
