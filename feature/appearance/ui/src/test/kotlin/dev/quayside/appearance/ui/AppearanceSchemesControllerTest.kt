@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.appearance.ui

import dev.quayside.appearance.api.CustomScheme
import dev.quayside.appearance.api.SampleSchemes
import dev.quayside.appearance.api.SchemeId
import dev.quayside.appearance.api.ThemeFileError
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Import of custom schemes from a theme file (FR-A6, plan AM-814), over fakes. */
class AppearanceSchemesControllerTest {
    private val mine = SampleSchemes.mine()
    private val other = mine.copy(id = SchemeId("other"), name = "Other")
    private val grey =
        mine.copy(id = SchemeId("grey"), colors = mine.colors.copy(text = SampleSchemes.color("#222222")))
    private val path = Path.of("/themes/schemes.json")

    private fun worldWith(vararg schemes: CustomScheme): DialogWorld =
        DialogWorld().also {
            it.files.putSchemes(path, schemes.toList())
            it.picker.openResult = path
        }

    @Test
    fun `import adds the schemes to the catalog and the draft`() =
        runTest {
            val world = worldWith(mine, other)
            val controller = opened(world)

            send(controller, AppearanceIntent.ImportSchemes)

            val state = controller.state.value.shouldNotBeNull()
            state.notice shouldBe FileNotice.SCHEMES_IMPORTED
            state.error.shouldBeNull()
            state.busy shouldBe false
            state.draft.settings.customSchemes shouldBe listOf(mine, other)
            world.fake.catalog.value
                .theme(mine.id)
                .shouldNotBeNull()
            world.fake.catalog.value
                .theme(other.id)
                .shouldNotBeNull()
            world.fake.settings.value.customSchemes shouldBe listOf(mine, other)
            world.fake.updates shouldBe 0
        }

    @Test
    fun `an imported scheme can be chosen and applied`() =
        runTest {
            val world = worldWith(mine)
            val controller = opened(world)
            send(controller, AppearanceIntent.ImportSchemes)

            send(controller, AppearanceIntent.SelectScheme(mine.id))
            controller.draft().settings.scheme shouldBe mine.id
            send(controller, AppearanceIntent.Apply)

            controller.state.value.shouldBeNull()
            world.fake.settings.value.scheme shouldBe mine.id
            world.fake.settings.value.customSchemes shouldBe listOf(mine)
        }

    @Test
    fun `importing settings keeps the schemes of the draft`() =
        runTest {
            val world = worldWith(mine)
            val controller = opened(world)
            send(controller, AppearanceIntent.ImportSchemes)

            world.picker.openResult = IMPORT_PATH
            send(controller, AppearanceIntent.Import)

            controller.draft().settings shouldBe IMPORTED.copy(customSchemes = listOf(mine))
        }

    @Test
    fun `export writes the schemes added in the dialog`() =
        runTest {
            val world = worldWith(mine)
            val controller = opened(world)
            send(controller, AppearanceIntent.ImportSchemes)

            send(controller, AppearanceIntent.Export)

            world.files.written[EXPORT_PATH]?.customSchemes shouldBe listOf(mine)
        }

    @Test
    fun `cancelled picker changes nothing`() =
        runTest {
            val world = worldWith(mine)
            world.picker.openResult = null
            val controller = opened(world)

            send(controller, AppearanceIntent.ImportSchemes)

            val state = controller.state.value.shouldNotBeNull()
            state.notice.shouldBeNull()
            state.error.shouldBeNull()
            world.fake.settings.value.customSchemes shouldBe emptyList()
        }

    @Test
    fun `file without schemes shows SCHEMES_NONE`() =
        runTest {
            val controller = opened(worldWith())

            send(controller, AppearanceIntent.ImportSchemes)

            controller.state.value
                .shouldNotBeNull()
                .error shouldBe ApplyError.SCHEMES_NONE
        }

    @Test
    fun `a taken id stops the import and keeps the earlier schemes`() =
        runTest {
            val world = worldWith(mine, other, mine)
            val controller = opened(world)

            send(controller, AppearanceIntent.ImportSchemes)

            val state = controller.state.value.shouldNotBeNull()
            state.error shouldBe ApplyError.SCHEME_TAKEN
            state.notice.shouldBeNull()
            world.fake.settings.value.customSchemes shouldBe listOf(mine, other)
        }

    @Test
    fun `low contrast shows SCHEME_CONTRAST and adds nothing`() =
        runTest {
            val world = worldWith(grey)
            val controller = opened(world)

            send(controller, AppearanceIntent.ImportSchemes)

            controller.state.value
                .shouldNotBeNull()
                .error shouldBe ApplyError.SCHEME_CONTRAST
            world.fake.catalog.value
                .theme(grey.id)
                .shouldBeNull()
        }

    @Test
    fun `a scheme that cannot be saved shows SCHEME_SAVE`() =
        runTest {
            val world = worldWith(mine)
            world.fake.failSaves = true
            val controller = opened(world)

            send(controller, AppearanceIntent.ImportSchemes)

            controller.state.value
                .shouldNotBeNull()
                .error shouldBe ApplyError.SCHEME_SAVE
        }

    @Test
    fun `an unreadable file shows the import category`() =
        runTest {
            val world = worldWith(mine)
            world.files.readFailure = ThemeFileError.InvalidValue
            val controller = opened(world)

            send(controller, AppearanceIntent.ImportSchemes)

            controller.state.value
                .shouldNotBeNull()
                .error shouldBe ApplyError.IMPORT_INVALID
        }

    @Test
    fun `intents while a file action runs are ignored`() =
        runTest {
            val world = DialogWorld(holdFiles = true).also { it.picker.openResult = path }
            world.files.putSchemes(path, listOf(mine))
            val controller = opened(world)
            send(controller, AppearanceIntent.ImportSchemes)

            send(controller, AppearanceIntent.ImportSchemes)

            world.heldFiles.reads shouldBe 1
        }
}
