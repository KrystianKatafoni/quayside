package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.ListDensity
import dev.quayside.appearance.api.UiScale
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Editing the draft of the Appearance dialog over fakes (FR-A3, FR-A4, plan AM-803 part A). */
class AppearanceControllerTest {
    @Test
    fun `closed until opened`() =
        runTest {
            controllerOf(DialogWorld()).state.value.shouldBeNull()
        }

    @Test
    fun `open starts the draft from current settings`() =
        runTest {
            val controller = opened()

            val state = controller.state.value.shouldNotBeNull()
            state.draft shouldBe AppearanceDraft(SAVED, terminalFont = "Menlo", terminalSize = 17)
            state.busy shouldBe false
            state.error.shouldBeNull()
        }

    @Test
    fun `select scheme changes only the draft`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)

            send(controller, AppearanceIntent.SelectScheme(SUNSET))

            controller.draft().settings.scheme shouldBe SUNSET
            world.fake.settings.value shouldBe SAVED
            world.terminal.settings.value.fontFamily shouldBe "Menlo"
            world.fake.updates shouldBe 0
            world.terminal.saves shouldBe 0
        }

    @Test
    fun `select scheme keeps typography`() =
        runTest {
            val controller = opened()

            send(controller, AppearanceIntent.SelectScheme(SUNSET))

            controller.draft().settings.typography shouldBe GEIST
        }

    @Test
    fun `select typography changes only the draft`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)

            send(controller, AppearanceIntent.SelectTypography(MONO))

            controller.draft().settings.typography shouldBe MONO
            controller.draft().settings.scheme shouldBe PAPER
            world.fake.settings.value shouldBe SAVED
        }

    @Test
    fun `select scale changes only the draft`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)

            send(controller, AppearanceIntent.SelectScale(UiScale.S))

            controller.draft().settings shouldBe SAVED.copy(uiScale = UiScale.S)
            world.fake.settings.value shouldBe SAVED
        }

    @Test
    fun `select density changes only the draft`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)

            send(controller, AppearanceIntent.SelectDensity(ListDensity.COMFORTABLE))

            controller.draft().settings shouldBe SAVED.copy(density = ListDensity.COMFORTABLE)
            world.fake.settings.value shouldBe SAVED
        }

    @Test
    fun `select terminal font changes only the draft`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)

            send(controller, AppearanceIntent.SelectTerminalFont("JetBrains Mono"))
            controller.draft().terminalFont shouldBe "JetBrains Mono"
            send(controller, AppearanceIntent.SelectTerminalFont(null))
            controller.draft().terminalFont.shouldBeNull()

            world.terminal.settings.value.fontFamily shouldBe "Menlo"
            world.terminal.saves shouldBe 0
        }

    @Test
    fun `step terminal size changes the draft by one and stops at the limits`() =
        runTest {
            val controller = opened()

            send(controller, AppearanceIntent.StepTerminalSize(1))
            controller.draft().terminalSize shouldBe 18
            send(controller, AppearanceIntent.StepTerminalSize(-1), AppearanceIntent.StepTerminalSize(-1))
            controller.draft().terminalSize shouldBe 16
            repeat(times = 40) { send(controller, AppearanceIntent.StepTerminalSize(1)) }
            controller.draft().terminalSize shouldBe 32
            repeat(times = 40) { send(controller, AppearanceIntent.StepTerminalSize(-1)) }
            controller.draft().terminalSize shouldBe 8
        }

    @Test
    fun `use suggested typography sets the theme default`() =
        runTest {
            // Given the draft on plex typography with the theme paper chosen, which suggests geist
            val world = DialogWorld(appearanceStart = AppearanceSettings())
            val controller = opened(world)
            send(controller, AppearanceIntent.SelectScheme(PAPER))
            controller.draft().settings.typography shouldBe PLEX_TYPOGRAPHY

            // When the user takes the suggestion
            send(controller, AppearanceIntent.UseSuggestedTypography)

            // Then the typography of the draft is the theme default, and nothing was applied
            controller.draft().settings.typography shouldBe GEIST
            world.fake.settings.value shouldBe AppearanceSettings()
        }

    @Test
    fun `use suggested typography does nothing for a theme without one`() =
        runTest {
            val world = DialogWorld(appearanceStart = AppearanceSettings())
            val controller = opened(world)

            send(controller, AppearanceIntent.SelectTypography(MONO), AppearanceIntent.UseSuggestedTypography)

            controller.draft().settings.typography shouldBe MONO
        }
}
