@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.commandpalette.ui

import dev.agentmanager.commandpalette.api.ChordKey
import dev.agentmanager.commandpalette.api.Command
import dev.agentmanager.commandpalette.api.FakeCommandProvider
import dev.agentmanager.commandpalette.api.FakeCommandRegistry
import dev.agentmanager.commandpalette.api.KeyChord
import dev.agentmanager.commandpalette.api.KeyModifier
import dev.agentmanager.commandpalette.api.Shortcut
import dev.agentmanager.commandpalette.api.aCommand
import dev.agentmanager.commandpalette.api.letterChord
import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.testing.RecordingLogger
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Window keys without ⌘ in the controller, on a fake registry (FR-K2; AM-409 plan, decisions 2 and 3). */
class CommandPaletteShortcutKeysTest {
    private class World(
        scope: TestScope,
    ) {
        val ran = mutableListOf<String>()
        val provider = FakeCommandProvider()
        val logger = RecordingLogger()
        val controller: CommandPaletteController

        init {
            val registry = FakeCommandRegistry(scope.backgroundScope)
            registry.register(provider)
            controller = CommandPaletteController(registry, scope.backgroundScope, logger)
            scope.runCurrent()
        }
    }

    private fun TestScope.world(): World = World(this)

    private fun TestScope.act(block: () -> Unit) {
        block()
        runCurrent()
    }

    private val g = letterChord('g')
    private val p = letterChord('p')
    private val j = letterChord('j')

    private fun World.keyed(
        id: String,
        vararg chords: KeyChord,
    ): Command = aCommand(id = id, shortcut = Shortcut(chords.toList()), run = { ran += id })

    private fun TestScope.press(
        world: World,
        chord: KeyChord,
    ): Boolean = world.controller.onChord(chord).also { runCurrent() }

    @Test
    fun `a bound letter runs its command once`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.keyed("sessions.next", j)))
            runCurrent()

            press(world, j) shouldBe true

            world.ran shouldBe listOf("sessions.next")
            world.controller.isOpen.value shouldBe false
        }

    @Test
    fun `g waits for the second key`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.keyed("projects.switch", g, p)))
            runCurrent()

            press(world, g) shouldBe true

            world.ran.shouldBeEmpty()
            world.logger.entries.shouldBeEmpty()
        }

    @Test
    fun `g then p runs the two key command`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.keyed("projects.switch", g, p)))
            runCurrent()

            press(world, g) shouldBe true
            press(world, p) shouldBe true

            world.ran shouldBe listOf("projects.switch")
            // The sequence is over: p alone is not a command.
            press(world, p) shouldBe false
            world.ran shouldBe listOf("projects.switch")
        }

    @Test
    fun `escape after g cancels`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.keyed("projects.switch", g, p), world.keyed("sessions.next", j)))
            runCurrent()
            press(world, g) shouldBe true

            press(world, KeyChord(ChordKey.Named.ESCAPE)) shouldBe true

            world.ran.shouldBeEmpty()
            // Nothing is pending any more: p does nothing and j runs as usual.
            press(world, p) shouldBe false
            press(world, j) shouldBe true
            world.ran shouldBe listOf("sessions.next")
        }

    @Test
    fun `another key after g cancels and runs nothing`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.keyed("projects.switch", g, p), world.keyed("sessions.next", j)))
            runCurrent()
            press(world, g) shouldBe true

            press(world, j) shouldBe true

            world.ran.shouldBeEmpty()
            press(world, j) shouldBe true
            world.ran shouldBe listOf("sessions.next")
        }

    @Test
    fun `an unbound key is not consumed`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.keyed("sessions.next", j)))
            runCurrent()

            press(world, letterChord('x')) shouldBe false

            world.ran.shouldBeEmpty()
        }

    @Test
    fun `meta chords are left to the menu`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.keyed("palette.open", letterChord('k', KeyModifier.META))))
            runCurrent()

            press(world, letterChord('k', KeyModifier.META)) shouldBe false

            world.ran.shouldBeEmpty()
        }

    @Test
    fun `keys are ignored while the palette is open`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.keyed("sessions.next", j)))
            runCurrent()
            act { world.controller.open() }

            press(world, j) shouldBe false

            world.ran.shouldBeEmpty()
            world.controller.isOpen.value shouldBe true
        }

    @Test
    fun `opening the palette clears a pending g`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.keyed("projects.switch", g, p)))
            runCurrent()
            press(world, g) shouldBe true

            act { world.controller.open() }
            act { world.controller.close() }

            press(world, p) shouldBe false
            world.ran.shouldBeEmpty()
        }

    @Test
    fun `closing the palette clears a pending g`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.keyed("projects.switch", g, p)))
            runCurrent()
            act { world.controller.open() }
            act { world.controller.close() }
            press(world, g) shouldBe true

            act { world.controller.close() }

            press(world, p) shouldBe false
            world.ran.shouldBeEmpty()
        }

    @Test
    fun `shortcut runs log the command id only`() =
        runTest {
            val world = world()
            val secret =
                aCommand(
                    id = "sessions.next",
                    title = "Sekretna sesja",
                    shortcut = Shortcut.of(j),
                    run = { world.ran += "secret" },
                )
            world.provider.set(listOf(secret))
            runCurrent()

            press(world, j) shouldBe true

            val entry = world.logger.entries.single()
            entry.level shouldBe LogLevel.INFO
            entry.message shouldBe "command run"
            entry.fields shouldBe mapOf("commandId" to "sessions.next")
            entry.message.lowercase() shouldNotContain "sekretna"
        }
}
