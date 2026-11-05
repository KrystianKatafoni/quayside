@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceError
import dev.quayside.appearance.api.ThemeFileError
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Export of the draft as a theme file, over fakes (FR-A6, plan AM-804). */
class AppearanceExportControllerTest {
    @Test
    fun `export writes the draft not the saved settings`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            send(controller, AppearanceIntent.SelectScheme(SUNSET), AppearanceIntent.ToggleDayNight)
            val draft = controller.draft()

            send(controller, AppearanceIntent.Export)

            world.files.written shouldBe mapOf(EXPORT_PATH to draft.settings)
            (draft.settings != SAVED) shouldBe true
            world.picker.suggestedNames.single() shouldEndWith ".json"
            world.fake.settings.value shouldBe SAVED
            world.fake.updates shouldBe 0
            world.terminal.saves shouldBe 0
        }

    @Test
    fun `export shows the exported notice and keeps the draft`() =
        runTest {
            val controller = opened()
            val draft = controller.draft()

            send(controller, AppearanceIntent.Export)

            val state = controller.state.value.shouldNotBeNull()
            state.notice shouldBe FileNotice.EXPORTED
            state.error.shouldBeNull()
            state.busy shouldBe false
            state.draft shouldBe draft
        }

    @Test
    fun `edit after export clears the notice`() =
        runTest {
            val controller = opened()
            send(controller, AppearanceIntent.Export)
            controller.state.value
                .shouldNotBeNull()
                .notice shouldBe FileNotice.EXPORTED

            send(controller, AppearanceIntent.SelectScheme(SUNSET))

            controller.state.value
                .shouldNotBeNull()
                .notice
                .shouldBeNull()
        }

    @Test
    fun `a cancelled file dialog after an export clears the notice`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            send(controller, AppearanceIntent.Export)
            world.picker.saveResult = null

            send(controller, AppearanceIntent.Export)

            controller.state.value
                .shouldNotBeNull()
                .notice
                .shouldBeNull()
        }

    @Test
    fun `the notice is cleared while the next file action runs`() =
        runTest {
            val controller = opened()
            send(controller, AppearanceIntent.Export)
            controller.state.value
                .shouldNotBeNull()
                .notice shouldBe FileNotice.EXPORTED

            controller.onIntent(AppearanceIntent.Export) // not run yet: the action is still waiting in the scope

            val running = controller.state.value.shouldNotBeNull()
            running.busy shouldBe true
            running.notice.shouldBeNull()
        }

    @Test
    fun `cancelled export picker writes nothing`() =
        runTest {
            val world = DialogWorld()
            world.picker.saveResult = null
            val controller = opened(world)
            val before = controller.state.value

            send(controller, AppearanceIntent.Export)

            controller.state.value shouldBe before
            world.files.written shouldBe emptyMap()
        }

    @Test
    fun `export error shows the export category`() =
        runTest {
            val world = DialogWorld()
            world.files.writeFailure = ThemeFileError.WriteFailed
            val controller = opened(world)
            val draft = controller.draft()

            send(controller, AppearanceIntent.Export)

            val state = controller.state.value.shouldNotBeNull()
            state.error shouldBe ApplyError.EXPORT
            state.notice.shouldBeNull()
            state.draft shouldBe draft
            state.busy shouldBe false
        }

    @Test
    fun `after a failed export the next export runs again`() =
        runTest {
            val world = DialogWorld()
            world.files.writeFailure = ThemeFileError.WriteFailed
            val controller = opened(world)
            send(controller, AppearanceIntent.Export)

            world.files.writeFailure = null
            send(controller, AppearanceIntent.Export)

            world.files.written.size shouldBe 1
            controller.state.value
                .shouldNotBeNull()
                .error
                .shouldBeNull()
            controller.state.value
                .shouldNotBeNull()
                .notice shouldBe FileNotice.EXPORTED
        }

    @Test
    fun `edits while exporting are ignored`() =
        runTest {
            val world = DialogWorld(holdFiles = true)
            val controller = opened(world)
            send(controller, AppearanceIntent.Export)
            controller.state.value
                .shouldNotBeNull()
                .busy shouldBe true

            send(controller, AppearanceIntent.SelectScheme(SUNSET), AppearanceIntent.Export)

            controller.draft() shouldBe AppearanceDraft(SAVED, "Menlo", 17)
            world.picker.suggestedNames.size shouldBe 1
        }

    @Test
    fun `export result after close is dropped on reopen`() =
        runTest {
            val world = DialogWorld(holdFiles = true)
            val controller = opened(world)
            send(controller, AppearanceIntent.Export)
            controller.close()
            controller.open()

            world.heldFiles.release.complete(Unit)
            runCurrent()

            val state = controller.state.value.shouldNotBeNull()
            state.notice.shouldBeNull()
            state.error.shouldBeNull()
            state.busy shouldBe false
        }

    @Test
    fun `a failed apply after import keeps the imported draft`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            send(controller, AppearanceIntent.Import)
            world.fake.failure = AppearanceError.StorageFailed

            send(controller, AppearanceIntent.Apply)

            val state = controller.state.value.shouldNotBeNull()
            state.error shouldBe ApplyError.APPEARANCE
            state.draft.settings shouldBe IMPORTED
        }
}
