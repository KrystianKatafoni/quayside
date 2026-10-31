package dev.quayside.commandpalette.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.commandpalette.api.Command
import dev.quayside.commandpalette.api.CommandGroup
import dev.quayside.commandpalette.api.CommandId
import dev.quayside.commandpalette.api.FakeCommandProvider
import dev.quayside.commandpalette.api.FakeCommandRegistry
import dev.quayside.commandpalette.api.KeyChord
import dev.quayside.commandpalette.api.Shortcut
import dev.quayside.commandpalette.api.aCommand
import dev.quayside.commandpalette.api.letterChord
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Window keys without ⌘ on a fake registry (FR-K2, AM-409). The harness puts `onKeyEvent(controller::onKeyEvent)`
 * on a focused `Box`, the way `Window(onKeyEvent)` does in the app; `runComposeUiTest` has no `Window`.
 */
@OptIn(ExperimentalTestApi::class)
class ShortcutKeysScreenTest {
    private val ran = mutableListOf<String>()

    private fun recording(
        id: String,
        title: String,
        vararg chords: KeyChord,
    ): Command =
        aCommand(
            id = id,
            title = title,
            group = CommandGroup.SESSION,
            shortcut = Shortcut(chords.toList()),
            run = { ran += id },
        )

    private val next = recording("sessions.next", "Jump to next", letterChord('j'))
    private val switch = recording("projects.switch", "Switch project", letterChord('g'), letterChord('p'))
    private val other = recording("sessions.previous", "Other", letterChord('k'))

    private fun ComposeUiTest.showKeys(vararg commands: Command): CommandPaletteController {
        val built = mutableListOf<CommandPaletteController>()
        setContent {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    val registry = FakeCommandRegistry(scope)
                    registry.register(FakeCommandProvider(commands.toList()))
                    CommandPaletteController(registry, scope, RecordingLogger()).also { built += it }
                }
            val focus = remember { FocusRequester() }
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(KEYS)
                    .focusRequester(focus)
                    .onKeyEvent(controller::onKeyEvent)
                    .focusable(),
            )
            CommandPalette(controller)
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
        waitUntil { built.isNotEmpty() }
        waitForIdle()
        return built.single()
    }

    private fun SemanticsNodeInteraction.press(key: Key) =
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }

    private fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
        waitUntilExactlyOneExists(hasTestTag(tag))
        return onNodeWithTag(tag)
    }

    private fun row(id: String): String = PaletteTags.row(CommandId(id))

    @Test
    fun `FR-K2 pressing j runs the command bound to j`() =
        runComposeUiTest {
            // Given a window with a command bound to j
            showKeys(next, switch)

            // When the user presses j
            onNodeWithTag(KEYS).press(Key.J)

            // Then the command ran once and the palette stays closed
            waitUntil { ran.isNotEmpty() }
            ran shouldBe listOf("sessions.next")
            onNodeWithTag(PaletteTags.ROOT).assertDoesNotExist()
        }

    @Test
    fun `FR-K2 g then p runs the two key command`() =
        runComposeUiTest {
            // Given a window with a command bound to g p
            showKeys(next, switch)

            // When the user presses g and then p
            onNodeWithTag(KEYS).press(Key.G)
            waitForIdle()
            ran.shouldBeEmpty()
            onNodeWithTag(KEYS).press(Key.P)

            // Then the command ran once
            waitUntil { ran.isNotEmpty() }
            ran shouldBe listOf("projects.switch")
        }

    @Test
    fun `FR-K2 g then Escape runs nothing`() =
        runComposeUiTest {
            // Given a window with commands on g p and j
            showKeys(next, switch)

            // When the user presses g and then Escape
            onNodeWithTag(KEYS).press(Key.G)
            onNodeWithTag(KEYS).press(Key.Escape)
            waitForIdle()

            // Then nothing ran, and p alone does not run the g p command
            onNodeWithTag(KEYS).press(Key.P)
            waitForIdle()
            ran.shouldBeEmpty()
        }

    @Test
    fun `FR-K2 typing j in the open palette only filters`() =
        runComposeUiTest {
            // Given the open palette over commands "Jump to next" (j) and "Other"
            val palette = showKeys(next, other)
            runOnIdle { palette.open() }
            await(row("sessions.next"))
            await(row("sessions.previous"))

            // When the user types j into the search field
            await(PaletteTags.FIELD).performTextInput("j")
            onNodeWithTag(PaletteTags.FIELD).press(Key.J)

            // Then the list is filtered and the command bound to j did not run
            await(row("sessions.next"))
            onNodeWithTag(row("sessions.previous")).assertDoesNotExist()
            ran.shouldBeEmpty()
        }

    private companion object {
        const val KEYS = "keys.harness"
    }
}
