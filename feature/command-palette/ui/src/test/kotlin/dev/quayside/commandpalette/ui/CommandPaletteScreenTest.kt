package dev.quayside.commandpalette.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.commandpalette.api.FakeCommandProvider
import dev.quayside.commandpalette.api.FakeCommandRegistry
import dev.quayside.commandpalette.api.KeyModifier
import dev.quayside.commandpalette.api.Shortcut
import dev.quayside.commandpalette.api.aCommand
import dev.quayside.commandpalette.api.letterChord
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** User scenarios of the palette on a fake registry (FR-K1, docs/12 §4). */
@OptIn(ExperimentalTestApi::class)
class CommandPaletteScreenTest {
    private val ran = mutableListOf<String>()

    private fun recording(
        id: String,
        title: String,
        keywords: List<String> = emptyList(),
        shortcut: Shortcut? = null,
        group: CommandGroup = CommandGroup.PROJECT,
    ): Command =
        aCommand(id = id, title = title, group = group, keywords = keywords, shortcut = shortcut, run = {
            ran +=
                id
        })

    private val add =
        recording(
            "projects.add",
            "Dodaj projekt…",
            shortcut = Shortcut.of(letterChord('o', KeyModifier.META, KeyModifier.SHIFT)),
        )
    private val rename = recording("projects.rename", "Zmień nazwę projektu…", keywords = listOf("rename", "nazwa"))
    private val remove = recording("projects.remove", "Usuń projekt", keywords = listOf("remove"))

    /** Shows the palette over the commands and opens it; the controller is built in the composition scope. */
    private fun ComposeUiTest.openPalette(vararg commands: Command): CommandPaletteController {
        val built = mutableListOf<CommandPaletteController>()
        setContent {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    val registry = FakeCommandRegistry(scope)
                    registry.register(FakeCommandProvider(commands.toList()))
                    CommandPaletteController(registry, scope, RecordingLogger()).also { built += it }
                }
            CommandPalette(controller)
        }
        waitUntil { built.isNotEmpty() }
        runOnIdle { built.single().open() }
        return built.single()
    }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag))
        return onNodeWithTag(tag)
    }

    private fun row(id: String): String = PaletteTags.row(CommandId(id))

    private fun SemanticsNodeInteraction.press(key: Key) =
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }

    @Test
    fun `FR-K1 typing usun finds Usuń projekt`() =
        runComposeUiTest {
            // Given the open palette with three project commands
            openPalette(add, rename, remove)
            await(row("projects.add"))

            // When the user types "usun"
            await(PaletteTags.FIELD).performTextInput("usun")

            // Then only "Usuń projekt" is listed
            await(row("projects.remove")).assertTextContains("Usuń projekt", substring = true)
            onNodeWithTag(row("projects.add")).assertDoesNotExist()
            onNodeWithTag(row("projects.rename")).assertDoesNotExist()
        }

    @Test
    fun `FR-K1 Enter runs the highlighted command and closes the palette`() =
        runComposeUiTest {
            // Given the palette filtered to "Usuń projekt"
            openPalette(add, rename, remove)
            await(PaletteTags.FIELD).performTextInput("usun")
            await(row("projects.remove")).assertIsSelected()

            // When the user presses Enter
            await(PaletteTags.FIELD).press(Key.Enter)

            // Then the command ran once and the palette is gone
            waitUntil { ran.isNotEmpty() }
            ran shouldBe listOf("projects.remove")
            waitUntilDoesNotExist(hasTestTag(PaletteTags.ROOT))
        }

    @Test
    fun `FR-K1 arrow down then Enter runs the second command`() =
        runComposeUiTest {
            // Given the open palette, the first command highlighted
            openPalette(add, rename, remove)
            await(row("projects.add")).assertIsSelected()

            // When the user presses arrow down and Enter
            await(PaletteTags.FIELD).press(Key.DirectionDown)
            await(row("projects.rename")).assertIsSelected()
            onNodeWithTag(row("projects.add")).assertIsNotSelected()
            onNodeWithTag(PaletteTags.FIELD).press(Key.Enter)

            // Then the second command ran
            waitUntil { ran.isNotEmpty() }
            ran shouldBe listOf("projects.rename")
            waitUntilDoesNotExist(hasTestTag(PaletteTags.ROOT))
        }

    @Test
    fun `FR-K1 Escape closes without running`() =
        runComposeUiTest {
            // Given the open palette
            openPalette(add, rename, remove)
            await(PaletteTags.FIELD).performTextInput("usun")

            // When the user presses Escape
            await(PaletteTags.FIELD).press(Key.Escape)

            // Then nothing ran and the palette is gone
            waitUntilDoesNotExist(hasTestTag(PaletteTags.ROOT))
            ran.shouldBeEmpty()
        }

    @Test
    fun `FR-K1 clicking a command runs it`() =
        runComposeUiTest {
            // Given the open palette
            openPalette(add, rename, remove)

            // When the user clicks "Zmień nazwę projektu…"
            await(row("projects.rename")).performClick()

            // Then it ran and the palette closed
            waitUntil { ran.isNotEmpty() }
            ran shouldBe listOf("projects.rename")
            waitUntilDoesNotExist(hasTestTag(PaletteTags.ROOT))
        }

    @Test
    fun `FR-K1 no match shows a fixed text`() =
        runComposeUiTest {
            // Given the open palette
            openPalette(add, rename, remove)

            // When the user types something that matches nothing
            await(PaletteTags.FIELD).performTextInput("zzzzzz")

            // Then the palette says so, without echoing the query
            await(PaletteTags.EMPTY).assertTextContains("Brak pasujących komend")
            onNodeWithTag(row("projects.add")).assertDoesNotExist()
        }

    @Test
    fun `FR-K1 shortcut is shown next to the title`() =
        runComposeUiTest {
            // Given the open palette with a command that has a shortcut
            openPalette(add, rename, remove)

            // Then its row shows the title and the shortcut text
            val shortcut = add.shortcut ?: error("test command has a shortcut")
            await(row("projects.add"))
                .assertTextContains("Dodaj projekt…", substring = true)
                .assertTextContains(ShortcutLabel.of(shortcut), substring = true)
        }

    @Test
    fun `a closed palette shows nothing`() =
        runComposeUiTest {
            // Given a palette that was never opened
            val built = mutableListOf<CommandPaletteController>()
            setContent {
                val scope = rememberCoroutineScope()
                val controller =
                    remember {
                        CommandPaletteController(FakeCommandRegistry(scope), scope, RecordingLogger()).also {
                            built +=
                                it
                        }
                    }
                CommandPalette(controller)
            }

            // Then there is no palette on the screen
            onNodeWithTag(PaletteTags.ROOT).assertDoesNotExist()
        }
}
