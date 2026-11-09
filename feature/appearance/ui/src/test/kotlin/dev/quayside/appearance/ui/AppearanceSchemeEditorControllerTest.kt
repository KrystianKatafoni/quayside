@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.appearance.ui

import dev.quayside.appearance.api.AppearanceSettings
import dev.quayside.appearance.api.DayNight
import dev.quayside.appearance.api.SampleSchemes
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeMode
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Adding, removing and exporting custom schemes in the dialog (FR-A6, plan AM-820), over fakes. */
class AppearanceSchemeEditorControllerTest {
    private val mine = SampleSchemes.mine()

    private fun typed(
        controller: AppearanceController,
        key: String = "mine",
        name: String = "Mine",
    ) = listOf(
        AppearanceIntent.OpenSchemeEditor,
        AppearanceIntent.EditSchemeField(EditorField.KEY, key),
        AppearanceIntent.EditSchemeField(EditorField.NAME, name),
    ).also { it.forEach(controller::onIntent) }

    private suspend fun worldWithMine(start: AppearanceSettings = SAVED): DialogWorld =
        DialogWorld(appearanceStart = start).also { it.fake.addScheme(mine) }

    @Test
    fun `the editor opens blank and closes without saving`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)

            send(controller, AppearanceIntent.OpenSchemeEditor)
            controller.state.value
                .shouldNotBeNull()
                .editor shouldBe SchemeEditor.blank()

            send(controller, AppearanceIntent.CloseSchemeEditor)
            controller.state.value
                .shouldNotBeNull()
                .editor
                .shouldBeNull()
            world.fake.settings.value.customSchemes shouldBe emptyList()
        }

    @Test
    fun `fields and mode edit the editor and keep the draft`() =
        runTest {
            val controller = opened()
            val before = controller.draft()

            send(
                controller,
                AppearanceIntent.OpenSchemeEditor,
                AppearanceIntent.EditSchemeField(EditorField.NAME, "Moj"),
                AppearanceIntent.EditSchemeMode(ThemeMode.LIGHT),
            )

            val editor =
                controller.state.value
                    .shouldNotBeNull()
                    .editor
                    .shouldNotBeNull()
            editor.values[EditorField.NAME] shouldBe "Moj"
            editor.mode shouldBe ThemeMode.LIGHT
            controller.draft() shouldBe before
        }

    @Test
    fun `saving adds the scheme, closes the editor and shows the notice`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            typed(controller)

            send(controller, AppearanceIntent.SaveScheme)

            val state = controller.state.value.shouldNotBeNull()
            state.editor.shouldBeNull()
            state.notice shouldBe FileNotice.SCHEME_ADDED
            state.error.shouldBeNull()
            state.draft.settings.customSchemes shouldBe listOf(mine.copy(radius = 6))
            world.fake.catalog.value
                .theme(mine.id)
                .shouldNotBeNull()
            world.fake.updates shouldBe 0
        }

    @Test
    fun `saving an invalid editor does nothing`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)
            typed(controller, key = "", name = "Mine")

            send(controller, AppearanceIntent.SaveScheme)

            controller.state.value
                .shouldNotBeNull()
                .editor
                .shouldNotBeNull()
            world.fake.settings.value.customSchemes shouldBe emptyList()
        }

    @Test
    fun `a taken key shows SCHEME_TAKEN and keeps the editor`() =
        runTest {
            val controller = opened()
            typed(controller, key = "plex")

            send(controller, AppearanceIntent.SaveScheme)

            val state = controller.state.value.shouldNotBeNull()
            state.error shouldBe ApplyError.SCHEME_TAKEN
            state.editor.shouldNotBeNull()
        }

    @Test
    fun `a scheme that cannot be saved shows SCHEME_SAVE and keeps the editor`() =
        runTest {
            val world = DialogWorld().also { it.fake.failSaves = true }
            val controller = opened(world)
            typed(controller)

            send(controller, AppearanceIntent.SaveScheme)

            val state = controller.state.value.shouldNotBeNull()
            state.error shouldBe ApplyError.SCHEME_SAVE
            state.editor.shouldNotBeNull()
        }

    @Test
    fun `removing a scheme drops it from the catalog and the draft`() =
        runTest {
            val world = worldWithMine()
            val controller = opened(world)

            send(controller, AppearanceIntent.RemoveScheme(mine.id))

            val state = controller.state.value.shouldNotBeNull()
            state.notice shouldBe FileNotice.SCHEME_REMOVED
            state.error.shouldBeNull()
            state.draft.settings.customSchemes shouldBe emptyList()
            world.fake.catalog.value
                .theme(mine.id)
                .shouldBeNull()
        }

    @Test
    fun `removing the scheme chosen in the draft puts the saved choice back`() =
        runTest {
            val saved = SAVED.copy(dayNight = DayNight(enabled = true, day = PAPER, night = PLEX))
            val world = worldWithMine(saved)
            val controller = opened(world)
            send(
                controller,
                AppearanceIntent.SelectScheme(mine.id),
                AppearanceIntent.SelectSlot(ThemeSlot.NIGHT),
                AppearanceIntent.SelectScheme(mine.id),
            )
            controller.draft().settings.dayNight shouldBe DayNight(enabled = true, day = mine.id, night = mine.id)

            send(controller, AppearanceIntent.RemoveScheme(mine.id))

            controller.draft().settings.dayNight shouldBe saved.dayNight
            controller.draft().settings.scheme shouldBe saved.scheme
        }

    @Test
    fun `removing the scheme chosen as the only theme puts the saved theme back`() =
        runTest {
            val controller = opened(worldWithMine())
            send(controller, AppearanceIntent.SelectScheme(mine.id))
            controller.draft().settings.scheme shouldBe mine.id

            send(controller, AppearanceIntent.RemoveScheme(mine.id))

            controller.draft().settings.scheme shouldBe SAVED.scheme
        }

    @Test
    fun `removing the saved scheme is refused`() =
        runTest {
            val world = worldWithMine(SAVED.copy(scheme = mine.id))
            val controller = opened(world)

            send(controller, AppearanceIntent.RemoveScheme(mine.id))

            val state = controller.state.value.shouldNotBeNull()
            state.error shouldBe ApplyError.SCHEME_IN_USE
            state.draft.settings.customSchemes shouldBe listOf(mine)
            world.fake.catalog.value
                .theme(mine.id)
                .shouldNotBeNull()
        }

    @Test
    fun `removing a built-in theme shows SCHEME_NOT_FOUND`() =
        runTest {
            val controller = opened()

            send(controller, AppearanceIntent.RemoveScheme(SUNSET))

            controller.state.value
                .shouldNotBeNull()
                .error shouldBe ApplyError.SCHEME_NOT_FOUND
        }

    @Test
    fun `export writes only the chosen scheme with default settings`() =
        runTest {
            val other = mine.copy(id = SchemeId("other"), name = "Other")
            val world = worldWithMine().also { it.fake.addScheme(other) }
            val controller = opened(world)

            send(controller, AppearanceIntent.ExportScheme(other.id))

            controller.state.value
                .shouldNotBeNull()
                .notice shouldBe FileNotice.EXPORTED
            world.files.written[EXPORT_PATH] shouldBe AppearanceSettings(customSchemes = listOf(other))
            world.picker.suggestedNames shouldBe listOf("other.json")
        }

    @Test
    fun `a cancelled scheme export writes nothing`() =
        runTest {
            val world = worldWithMine().also { it.picker.saveResult = null }
            val controller = opened(world)

            send(controller, AppearanceIntent.ExportScheme(mine.id))

            world.files.written shouldBe emptyMap()
            controller.state.value
                .shouldNotBeNull()
                .notice
                .shouldBeNull()
        }

    @Test
    fun `exporting an unknown scheme asks for no file`() =
        runTest {
            val world = DialogWorld()
            val controller = opened(world)

            send(controller, AppearanceIntent.ExportScheme(SUNSET))

            controller.state.value
                .shouldNotBeNull()
                .error shouldBe ApplyError.SCHEME_NOT_FOUND
            world.picker.suggestedNames shouldBe emptyList()
        }

    @Test
    fun `scheme intents are ignored while a file action runs`() =
        runTest {
            val controller = opened(DialogWorld(holdFiles = true))
            send(controller, AppearanceIntent.Import)

            send(controller, AppearanceIntent.OpenSchemeEditor)

            controller.state.value
                .shouldNotBeNull()
                .editor
                .shouldBeNull()
        }
}
