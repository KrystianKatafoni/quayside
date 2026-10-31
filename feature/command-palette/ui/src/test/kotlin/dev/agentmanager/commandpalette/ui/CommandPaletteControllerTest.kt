@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.commandpalette.ui

import dev.agentmanager.commandpalette.api.Command
import dev.agentmanager.commandpalette.api.CommandGroup
import dev.agentmanager.commandpalette.api.FakeCommandProvider
import dev.agentmanager.commandpalette.api.FakeCommandRegistry
import dev.agentmanager.commandpalette.api.aCommand
import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.testing.RecordingLogger
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** State holder of the ⌘K palette on a fake registry (FR-K1; AM-408 plan, decisions 3 to 5). */
class CommandPaletteControllerTest {
    private class World(
        scope: TestScope,
        commands: List<Command>,
    ) {
        val ran = mutableListOf<String>()
        val provider = FakeCommandProvider(commands)
        val logger = RecordingLogger()
        val controller: CommandPaletteController

        init {
            val registry = FakeCommandRegistry(scope.backgroundScope)
            registry.register(provider)
            controller = CommandPaletteController(registry, scope.backgroundScope, logger)
            scope.runCurrent()
        }
    }

    private fun TestScope.world(vararg commands: Command): World = World(this, commands.toList())

    private fun World.recording(
        id: String,
        title: String = id,
        group: CommandGroup = CommandGroup.APP,
    ): Command = aCommand(id = id, title = title, group = group, run = { ran += id })

    private fun TestScope.act(block: () -> Unit) {
        block()
        runCurrent()
    }

    private fun World.ids(): List<String> =
        controller.view.value.results
            .map { it.id.value }

    @Test
    fun `palette starts closed`() =
        runTest {
            val world = world(aCommand())

            world.controller.isOpen.value shouldBe false
            world.controller.view.value.open shouldBe false
        }

    @Test
    fun `open shows every command in group order`() =
        runTest {
            val world =
                world(
                    aCommand(id = "session", group = CommandGroup.SESSION),
                    aCommand(id = "goto", group = CommandGroup.GO_TO_PROJECT),
                    aCommand(id = "project", group = CommandGroup.PROJECT),
                )

            act { world.controller.open() }

            world.controller.isOpen.value shouldBe true
            world.ids() shouldBe listOf("project", "goto", "session")
            world.controller.view.value.query shouldBe ""
            world.controller.view.value.highlighted shouldBe 0
        }

    @Test
    fun `open with a group shows only that group`() =
        runTest {
            val world =
                world(
                    aCommand(id = "project", group = CommandGroup.PROJECT),
                    aCommand(id = "goto", group = CommandGroup.GO_TO_PROJECT),
                )

            act { world.controller.open(CommandGroup.GO_TO_PROJECT) }

            world.ids() shouldBe listOf("goto")
        }

    @Test
    fun `open while open keeps the query`() =
        runTest {
            val world = world(aCommand(id = "a", title = "Alpha"), aCommand(id = "b", title = "Beta"))
            act { world.controller.open() }
            act { world.controller.onEvent(PaletteEvent.QueryChanged("alp")) }

            act { world.controller.open() }

            world.controller.view.value.query shouldBe "alp"
            world.ids() shouldBe listOf("a")
        }

    @Test
    fun `typing filters ignoring Polish letters`() =
        runTest {
            val world =
                world(aCommand(id = "add", title = "Dodaj projekt…"), aCommand(id = "remove", title = "Usuń projekt"))
            act { world.controller.open() }

            act { world.controller.onEvent(PaletteEvent.QueryChanged("usun")) }

            world.ids() shouldBe listOf("remove")
        }

    @Test
    fun `confirm closes and runs the highlighted command once`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.recording("first"), world.recording("second"), world.recording("third")))
            runCurrent()
            act { world.controller.open() }
            act { world.controller.onEvent(PaletteEvent.MoveDown) }

            act { world.controller.onEvent(PaletteEvent.Confirm) }

            world.ran shouldBe listOf("second")
            world.controller.isOpen.value shouldBe false
        }

    @Test
    fun `picking a row runs that command and closes`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.recording("first"), world.recording("second")))
            runCurrent()
            act { world.controller.open() }

            act { world.controller.onEvent(PaletteEvent.Pick(1)) }

            world.ran shouldBe listOf("second")
            world.controller.isOpen.value shouldBe false
        }

    @Test
    fun `a second confirm is ignored`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.recording("first")))
            runCurrent()
            act { world.controller.open() }

            act {
                world.controller.onEvent(PaletteEvent.Confirm)
                world.controller.onEvent(PaletteEvent.Confirm)
            }

            world.ran shouldBe listOf("first")
            world.logger.entriesAt(LogLevel.INFO).size shouldBe 1
        }

    @Test
    fun `confirm without results does nothing`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.recording("first")))
            runCurrent()
            act { world.controller.open() }
            act { world.controller.onEvent(PaletteEvent.QueryChanged("zzzz")) }

            act { world.controller.onEvent(PaletteEvent.Confirm) }

            world.ran.shouldBeEmpty()
            world.controller.isOpen.value shouldBe true
            world.logger.entries.shouldBeEmpty()
        }

    @Test
    fun `dismiss closes without running`() =
        runTest {
            val world = world()
            world.provider.set(listOf(world.recording("first")))
            runCurrent()
            act { world.controller.open() }

            act { world.controller.onEvent(PaletteEvent.Dismiss) }
            world.controller.isOpen.value shouldBe false
            act { world.controller.open() }
            act { world.controller.close() }

            world.controller.isOpen.value shouldBe false
            world.ran.shouldBeEmpty()
        }

    @Test
    fun `reopening starts with an empty query`() =
        runTest {
            val world = world(aCommand(id = "a", title = "Alpha"), aCommand(id = "b", title = "Beta"))
            act { world.controller.open() }
            act { world.controller.onEvent(PaletteEvent.QueryChanged("alp")) }
            act { world.controller.close() }

            act { world.controller.open() }

            world.controller.view.value.query shouldBe ""
            world.ids() shouldBe listOf("a", "b")
        }

    @Test
    fun `shrinking commands keep the highlight in range`() =
        runTest {
            val world = world(aCommand(id = "a"), aCommand(id = "b"), aCommand(id = "c"))
            act { world.controller.open() }
            act { world.controller.onEvent(PaletteEvent.MoveDown) }
            act { world.controller.onEvent(PaletteEvent.MoveDown) }
            world.controller.view.value.highlighted shouldBe 2

            act { world.provider.set(listOf(aCommand(id = "a"))) }

            world.controller.view.value.highlighted shouldBe 0
            world.ids() shouldBe listOf("a")
        }

    @Test
    fun `logs hold command ids only`() =
        runTest {
            val world = world()
            val secret = aCommand(id = "projects.goto.p1", title = "Sekretny projekt", run = { world.ran += "secret" })
            world.provider.set(listOf(secret))
            runCurrent()
            act { world.controller.open() }
            act { world.controller.onEvent(PaletteEvent.QueryChanged("sekretny")) }

            act { world.controller.onEvent(PaletteEvent.Confirm) }

            world.ran shouldBe listOf("secret")
            val entries = world.logger.entries
            entries.size shouldBe 1
            entries.single().level shouldBe LogLevel.INFO
            entries.single().message shouldBe "command run"
            entries.single().fields shouldBe mapOf("commandId" to "projects.goto.p1")
            entries.forEach { entry ->
                withClue(entry) {
                    entry.message.lowercase() shouldNotContain "sekretny"
                    entry.fields.values.forEach { it.toString().lowercase() shouldNotContain "sekretny" }
                }
            }
        }
}
