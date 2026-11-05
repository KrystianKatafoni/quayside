@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.appearance.ui

import dev.quayside.appearance.api.ThemeFileError
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** Import of theme files into the draft, over fakes (FR-A6, plan AM-804). */
class AppearanceImportControllerTest {
    @Test
    fun `import puts the file into the draft without saving`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)

            send(controller, AppearanceIntent.Import)

            val state = controller.state.value.shouldNotBeNull()
            state.draft.settings shouldBe IMPORTED
            state.draft.terminalFont shouldBe SAVED_TERMINAL.fontFamily
            state.draft.terminalSize shouldBe SAVED_TERMINAL.fontSize
            state.notice shouldBe FileNotice.IMPORTED
            state.error.shouldBeNull()
            state.busy shouldBe false
            world.heldFiles.reads shouldBe 1
            world.files.reads shouldBe listOf(IMPORT_PATH)
            world.fake.settings.value shouldBe SAVED
            world.fake.updates shouldBe 0
            world.terminal.saves shouldBe 0
        }

    @Test
    fun `apply after import saves the imported settings`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            send(controller, AppearanceIntent.Import)

            send(controller, AppearanceIntent.Apply)

            controller.state.value.shouldBeNull()
            world.fake.settings.value shouldBe IMPORTED
            world.terminal.settings.value shouldBe SAVED_TERMINAL
        }

    @Test
    fun `cancel after import saves nothing`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            send(controller, AppearanceIntent.Import)

            controller.close()
            runCurrent()
            controller.open()

            controller.draft() shouldBe AppearanceDraft(SAVED, "Menlo", 17)
            world.fake.settings.value shouldBe SAVED
            controller.state.value
                .shouldNotBeNull()
                .notice
                .shouldBeNull()
        }

    @Test
    fun `cancelled import picker changes nothing`() =
        runTest {
            val world = DialogWorld()
            world.picker.openResult = null
            val controller = opened(world)
            val before = controller.state.value

            send(controller, AppearanceIntent.Import)

            controller.state.value shouldBe before
            world.picker.opens shouldBe 1
            world.heldFiles.reads shouldBe 0
        }

    @ParameterizedTest(name = "{0} shows {1}")
    @MethodSource("importErrors")
    fun `import error shows the category and keeps the draft`(
        failure: ThemeFileError,
        expected: ApplyError,
    ) = runTest {
        val world = DialogWorld()
        world.files.readFailure = failure
        val controller = opened(world)
        send(controller, AppearanceIntent.SelectScheme(SUNSET))
        val draft = controller.draft()

        send(controller, AppearanceIntent.Import)

        val state = controller.state.value.shouldNotBeNull()
        state.error shouldBe expected
        state.draft shouldBe draft
        state.notice.shouldBeNull()
        state.busy shouldBe false
        world.fake.settings.value shouldBe SAVED
    }

    @Test
    fun `edits while importing are ignored`() =
        runTest {
            val world = DialogWorld(holdFiles = true)
            val controller = opened(world)
            send(controller, AppearanceIntent.Import)
            controller.state.value
                .shouldNotBeNull()
                .busy shouldBe true

            send(
                controller,
                AppearanceIntent.SelectScheme(SUNSET),
                AppearanceIntent.ToggleDayNight,
                AppearanceIntent.StepTerminalSize(1),
                AppearanceIntent.Import,
                AppearanceIntent.Export,
                AppearanceIntent.Apply,
            )

            controller.draft() shouldBe AppearanceDraft(SAVED, "Menlo", 17)
            world.heldFiles.reads shouldBe 1
            world.picker.opens shouldBe 1
            world.picker.suggestedNames shouldBe emptyList()
            world.appearance.calls shouldBe 0
        }

    @Test
    fun `after a failed import the next import runs again`() =
        runTest {
            val world = DialogWorld()
            world.files.readFailure = ThemeFileError.ReadFailed
            val controller = opened(world)
            send(controller, AppearanceIntent.Import)
            controller.state.value
                .shouldNotBeNull()
                .error shouldBe ApplyError.IMPORT_UNREADABLE

            world.files.readFailure = null
            send(controller, AppearanceIntent.Import)

            world.heldFiles.reads shouldBe 2
            val state = controller.state.value.shouldNotBeNull()
            state.error.shouldBeNull()
            state.busy shouldBe false
            state.draft.settings shouldBe IMPORTED
        }

    @Test
    fun `import result after close is dropped on reopen`() =
        runTest {
            val world = DialogWorld(holdFiles = true)
            val controller = opened(world)
            send(controller, AppearanceIntent.Import)
            controller.close()
            controller.open()

            world.heldFiles.release.complete(Unit)
            runCurrent()

            val state = controller.state.value.shouldNotBeNull()
            state.draft shouldBe AppearanceDraft(SAVED, "Menlo", 17)
            state.busy shouldBe false
            state.error.shouldBeNull()
            state.notice.shouldBeNull()
        }

    @Test
    fun `failed import result after close is dropped on reopen`() =
        runTest {
            val world = DialogWorld(holdFiles = true)
            world.files.readFailure = ThemeFileError.NotThemeFile
            val controller = opened(world)
            send(controller, AppearanceIntent.Import)
            controller.close()
            controller.open()

            world.heldFiles.release.complete(Unit)
            runCurrent()

            val state = controller.state.value.shouldNotBeNull()
            state.error.shouldBeNull()
            state.busy shouldBe false
        }

    @Test
    fun `import works again after close during a running import`() =
        runTest {
            val world = DialogWorld(holdFiles = true)
            val controller = opened(world)
            send(controller, AppearanceIntent.Import)
            controller.close()
            controller.open()
            world.heldFiles.release.complete(Unit)
            runCurrent()

            send(controller, AppearanceIntent.Import)

            world.heldFiles.reads shouldBe 2
            controller.draft().settings shouldBe IMPORTED
        }

    companion object {
        @JvmStatic
        fun importErrors(): Stream<Arguments> =
            Stream.of(
                Arguments.of(ThemeFileError.ReadFailed, ApplyError.IMPORT_UNREADABLE),
                Arguments.of(ThemeFileError.NotThemeFile, ApplyError.IMPORT_NOT_THEME),
                Arguments.of(ThemeFileError.NewerVersion(2), ApplyError.IMPORT_NEWER),
                Arguments.of(ThemeFileError.InvalidValue, ApplyError.IMPORT_INVALID),
            )
    }
}
