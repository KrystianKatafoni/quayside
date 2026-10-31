@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.app

import dev.agentmanager.commandpalette.api.ChordKey
import dev.agentmanager.commandpalette.api.CommandGroup
import dev.agentmanager.commandpalette.api.FakeCommandProvider
import dev.agentmanager.commandpalette.api.FakeCommandRegistry
import dev.agentmanager.commandpalette.api.KeyChord
import dev.agentmanager.commandpalette.api.KeyModifier
import dev.agentmanager.commandpalette.api.Shortcut
import dev.agentmanager.commandpalette.api.aCommand
import dev.agentmanager.commandpalette.api.letterChord
import dev.agentmanager.commandpalette.ui.CommandPaletteController
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.terminal.ui.TerminalKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.event.KeyEvent
import java.util.stream.Stream

/** ⌘ keys from a focused terminal reach the app shortcuts (AM-503, decision 9, table 4). */
class TerminalKeysTest {
    private fun key(
        code: Int,
        meta: Boolean = true,
        shift: Boolean = false,
        alt: Boolean = false,
        ctrl: Boolean = false,
    ) = TerminalKey(code, meta, shift, alt, ctrl)

    @ParameterizedTest(name = "{0}")
    @MethodSource("chords")
    fun `terminal keys map to chords`(
        key: TerminalKey,
        chord: KeyChord?,
    ) {
        chordOf(key) shouldBe chord
    }

    private class World(
        scope: TestScope,
    ) {
        val ran = mutableListOf<String>()
        val registry = FakeCommandRegistry(scope.backgroundScope)
        val palette = CommandPaletteController(registry, scope.backgroundScope, RecordingLogger())
        val keys = TerminalKeys(registry, palette, scope.backgroundScope)

        init {
            registry.register(
                FakeCommandProvider(
                    listOf(
                        aCommand(
                            id = "projects.add",
                            group = CommandGroup.PROJECT,
                            shortcut = Shortcut.of(letterChord('o', KeyModifier.META, KeyModifier.SHIFT)),
                            run = { ran += "projects.add" },
                        ),
                        aCommand(
                            id = "projects.remove",
                            group = CommandGroup.PROJECT,
                            shortcut = Shortcut.of(KeyChord(ChordKey.Named.BACKSPACE, setOf(KeyModifier.META))),
                            run = { ran += "projects.remove" },
                        ),
                        aCommand(
                            id = "sequence",
                            shortcut = Shortcut.of(letterChord('g'), letterChord('p')),
                            run = { ran += "sequence" },
                        ),
                    ),
                ),
            )
            scope.runCurrent()
        }
    }

    @Test
    fun `meta K opens the palette`() =
        runTest {
            val world = World(this)

            world.keys.onKey(key(KeyEvent.VK_K)) shouldBe true
            runCurrent()

            world.palette.isOpen.value shouldBe true
        }

    @Test
    fun `meta shift O runs add project`() =
        runTest {
            val world = World(this)

            world.keys.onKey(key(KeyEvent.VK_O, shift = true)) shouldBe true
            runCurrent()

            world.ran shouldBe listOf("projects.add")
            world.palette.isOpen.value shouldBe false
        }

    @Test
    fun `meta C is not consumed`() =
        runTest {
            val world = World(this)

            world.keys.onKey(key(KeyEvent.VK_C)) shouldBe false
            runCurrent()

            world.ran shouldBe emptyList()
            world.palette.isOpen.value shouldBe false
        }

    @Test
    fun `meta backspace edits text and never removes a project`() =
        runTest {
            val world = World(this)

            world.keys.onKey(key(KeyEvent.VK_BACK_SPACE)) shouldBe false
            world.keys.onKey(key(KeyEvent.VK_UP)) shouldBe false
            runCurrent()

            world.ran shouldBe emptyList()
        }

    @Test
    fun `keys without meta are not consumed`() =
        runTest {
            val world = World(this)

            world.keys.onKey(key(KeyEvent.VK_K, meta = false)) shouldBe false
            world.keys.onKey(key(KeyEvent.VK_G, meta = false)) shouldBe false
            runCurrent()

            world.palette.isOpen.value shouldBe false
            world.ran shouldBe emptyList()
        }

    companion object {
        private val meta = setOf(KeyModifier.META)

        @JvmStatic
        fun chords(): Stream<Arguments> =
            Stream.of(
                Arguments.of(TerminalKey(KeyEvent.VK_K, true, false, false, false), letter('k', meta)),
                Arguments.of(
                    TerminalKey(KeyEvent.VK_O, true, true, false, false),
                    letter('o', meta + KeyModifier.SHIFT),
                ),
                Arguments.of(
                    TerminalKey(KeyEvent.VK_J, true, false, true, true),
                    letter('j', meta + KeyModifier.ALT + KeyModifier.CTRL),
                ),
                Arguments.of(
                    TerminalKey(KeyEvent.VK_BACK_SPACE, true, false, false, false),
                    KeyChord(ChordKey.Named.BACKSPACE, meta),
                ),
                Arguments.of(TerminalKey(KeyEvent.VK_1, true, false, false, false), letter('1', meta)),
                Arguments.of(TerminalKey(KeyEvent.VK_F5, true, false, false, false), null),
                Arguments.of(TerminalKey(KeyEvent.VK_META, true, false, false, false), null),
            )

        private fun letter(
            char: Char,
            modifiers: Set<KeyModifier>,
        ) = KeyChord(ChordKey.Letter(char), modifiers)
    }
}
