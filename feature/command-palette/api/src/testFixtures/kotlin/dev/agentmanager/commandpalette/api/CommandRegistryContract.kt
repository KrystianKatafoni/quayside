@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.agentmanager.commandpalette.api

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

/**
 * Behaviour every [CommandRegistry] must have (ENG-T2). The registry keeps its list hot in
 * `backgroundScope`; tests call `runCurrent()` after every change instead of advancing the clock.
 */
public abstract class CommandRegistryContract {
    /** Returns a fresh, empty registry that works in this test's `backgroundScope`. */
    public abstract fun TestScope.create(): CommandRegistry

    private fun CommandRegistry.ids(): List<String> = commands.value.map { it.id.value }

    @Test
    public fun `empty registry has no commands`(): Unit =
        runTest {
            val registry = create()
            runCurrent()

            registry.commands.value shouldBe emptyList()
        }

    @Test
    public fun `commands follow registration order`(): Unit =
        runTest {
            val registry = create()
            registry.register(FakeCommandProvider(listOf(aCommand("a1"), aCommand("a2"))))
            registry.register(FakeCommandProvider(listOf(aCommand("b1"))))
            runCurrent()

            registry.ids() shouldBe listOf("a1", "a2", "b1")
        }

    @Test
    public fun `provider updates are reflected`(): Unit =
        runTest {
            val registry = create()
            val first = FakeCommandProvider(listOf(aCommand("a1")))
            registry.register(first)
            registry.register(FakeCommandProvider(listOf(aCommand("b1"))))
            runCurrent()

            first.set(listOf(aCommand("a1"), aCommand("a2")))
            runCurrent()

            registry.ids() shouldBe listOf("a1", "a2", "b1")
        }

    @Test
    public fun `closing removes the provider commands`(): Unit =
        runTest {
            val registry = create()
            val handle = registry.register(FakeCommandProvider(listOf(aCommand("a1"))))
            registry.register(FakeCommandProvider(listOf(aCommand("b1"))))
            runCurrent()

            handle.close()
            runCurrent()

            registry.ids() shouldBe listOf("b1")
        }

    @Test
    public fun `closing twice is harmless`(): Unit =
        runTest {
            val registry = create()
            val handle = registry.register(FakeCommandProvider(listOf(aCommand("a1"))))
            registry.register(FakeCommandProvider(listOf(aCommand("b1"))))
            runCurrent()

            handle.close()
            handle.close()
            runCurrent()

            registry.ids() shouldBe listOf("b1")
        }

    @Test
    public fun `duplicate id keeps the first command`(): Unit =
        runTest {
            val registry = create()
            registry.register(FakeCommandProvider(listOf(aCommand("same", title = "First"))))
            registry.register(FakeCommandProvider(listOf(aCommand("same", title = "Second"), aCommand("other"))))
            runCurrent()

            registry.commands.value.map { it.id.value to it.title } shouldBe
                listOf("same" to "First", "other" to "Test command")
        }

    @Test
    public fun `duplicate shortcut keeps the first binding and the command`(): Unit =
        runTest {
            val registry = create()
            val shortcut = Shortcut.of(letterChord('k', KeyModifier.META))
            registry.register(FakeCommandProvider(listOf(aCommand("first", shortcut = shortcut))))
            registry.register(FakeCommandProvider(listOf(aCommand("second", shortcut = shortcut))))
            runCurrent()

            registry.commands.value.map { it.id.value to it.shortcut } shouldBe
                listOf("first" to shortcut, "second" to null)
        }

    @Test
    public fun `register works from another thread`(): Unit =
        runTest {
            val registry = create()

            val handle =
                withContext(Dispatchers.Default) {
                    registry.register(FakeCommandProvider(listOf(aCommand("a1"))))
                }
            runCurrent()
            registry.ids() shouldBe listOf("a1")

            withContext(Dispatchers.Default) { handle.close() }
            runCurrent()
            registry.ids() shouldBe emptyList()
        }
}
