@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.appearance.ui

import dev.quayside.appearance.api.DayNight
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Day/night in the draft of the dialog, over fakes (FR-A5, plan AM-804). */
class AppearanceDayNightControllerTest {
    @Test
    fun `toggle day night changes only the draft`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)

            send(controller, AppearanceIntent.ToggleDayNight)

            controller.draft().settings.dayNight shouldBe DayNight(enabled = true)
            controller.draft().settings.copy(dayNight = DayNight()) shouldBe SAVED
            world.fake.settings.value shouldBe SAVED
            world.fake.updates shouldBe 0
            world.terminal.saves shouldBe 0
        }

    @Test
    fun `toggle twice turns day night off again`() =
        runTest {
            val controller = opened()

            send(controller, AppearanceIntent.ToggleDayNight, AppearanceIntent.ToggleDayNight)

            controller
                .draft()
                .settings.dayNight.enabled shouldBe false
        }

    @Test
    fun `toggle keeps the day and night themes already chosen`() =
        runTest {
            val controller = opened(DialogWorld(appearanceStart = SAVED.copy(dayNight = DayNight(false, SUNSET, PLEX))))

            send(controller, AppearanceIntent.ToggleDayNight)

            controller.draft().settings.dayNight shouldBe DayNight(true, SUNSET, PLEX)
        }

    @Test
    fun `select theme in night slot sets the night scheme`() =
        runTest {
            val controller = opened()
            send(controller, AppearanceIntent.ToggleDayNight, AppearanceIntent.SelectSlot(ThemeSlot.NIGHT))

            send(controller, AppearanceIntent.SelectScheme(SUNSET))

            controller.draft().settings.dayNight shouldBe DayNight(true, DayNight.DEFAULT_DAY, SUNSET)
            controller.draft().settings.scheme shouldBe SAVED.scheme
        }

    @Test
    fun `select theme in day slot sets the day scheme`() =
        runTest {
            val controller = opened()
            send(controller, AppearanceIntent.ToggleDayNight)

            send(controller, AppearanceIntent.SelectScheme(SUNSET))

            controller.draft().settings.dayNight shouldBe DayNight(true, SUNSET, DayNight().night)
            controller.draft().settings.scheme shouldBe SAVED.scheme
        }

    @Test
    fun `select theme with day night off sets the scheme`() =
        runTest {
            val controller = opened()
            send(controller, AppearanceIntent.SelectSlot(ThemeSlot.NIGHT))

            send(controller, AppearanceIntent.SelectScheme(SUNSET))

            controller.draft().settings.scheme shouldBe SUNSET
            controller.draft().settings.dayNight shouldBe DayNight()
        }

    @Test
    fun `select slot changes the preview scheme`() =
        runTest {
            val controller = opened(DialogWorld(appearanceStart = SAVED.copy(dayNight = DayNight(true, PLEX, SUNSET))))

            previewScheme(controller.draft()) shouldBe PLEX
            send(controller, AppearanceIntent.SelectSlot(ThemeSlot.NIGHT))

            controller.draft().slot shouldBe ThemeSlot.NIGHT
            previewScheme(controller.draft()) shouldBe SUNSET
            send(controller, AppearanceIntent.SelectSlot(ThemeSlot.DAY))
            previewScheme(controller.draft()) shouldBe PLEX
        }

    @Test
    fun `apply saves the day and night themes`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            send(
                controller,
                AppearanceIntent.ToggleDayNight,
                AppearanceIntent.SelectScheme(PLEX),
                AppearanceIntent.SelectSlot(ThemeSlot.NIGHT),
                AppearanceIntent.SelectScheme(SUNSET),
            )

            send(controller, AppearanceIntent.Apply)

            controller.state.value.shouldBeNull()
            world.fake.settings.value shouldBe SAVED.copy(dayNight = DayNight(true, PLEX, SUNSET))
        }

    @Test
    fun `the dialog opens on the day slot`() =
        runTest {
            val controller = opened()

            controller.draft().slot shouldBe ThemeSlot.DAY
        }
}
