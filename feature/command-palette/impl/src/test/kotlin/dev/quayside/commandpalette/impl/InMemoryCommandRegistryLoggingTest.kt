@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.commandpalette.impl

import dev.quayside.commandpalette.api.FakeCommandProvider
import dev.quayside.commandpalette.api.KeyModifier
import dev.quayside.commandpalette.api.Shortcut
import dev.quayside.commandpalette.api.aCommand
import dev.quayside.commandpalette.api.letterChord
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.testing.RecordingLogger
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Log content rules of ENG-C8: categories and command ids only, never titles. */
class InMemoryCommandRegistryLoggingTest {
    private val secretTitle = "Przejdź do projektu: SekretnyKlient"

    private fun TestScope.registry(logger: RecordingLogger) = InMemoryCommandRegistry(backgroundScope, logger)

    @Test
    fun `duplicates are logged with ids only`() =
        runTest {
            val logger = RecordingLogger()
            val registry = registry(logger)
            val shortcut = Shortcut.of(letterChord('k', KeyModifier.META))
            registry.register(FakeCommandProvider(listOf(aCommand("first", shortcut = shortcut))))
            registry.register(
                FakeCommandProvider(
                    listOf(aCommand("first"), aCommand("second", shortcut = shortcut)),
                ),
            )
            runCurrent()

            val warnings = logger.entriesAt(LogLevel.WARN)
            warnings.map { it.fields["reason"] to it.fields["commandId"] } shouldContain ("duplicateId" to "first")
            warnings.map { it.fields["reason"] to it.fields["commandId"] } shouldContain
                ("duplicateShortcut" to "second")
        }

    @Test
    fun `a persisting conflict is logged once`() =
        runTest {
            val logger = RecordingLogger()
            val registry = registry(logger)
            registry.register(FakeCommandProvider(listOf(aCommand("same"))))
            val second = FakeCommandProvider(listOf(aCommand("same")))
            registry.register(second)
            runCurrent()

            second.set(listOf(aCommand("same"), aCommand("other")))
            runCurrent()

            logger.entriesAt(LogLevel.WARN).size shouldBe 1
        }

    @Test
    fun `titles never reach the log`() =
        runTest {
            val logger = RecordingLogger()
            val registry = registry(logger)
            val shortcut = Shortcut.of(letterChord('k', KeyModifier.META))
            registry.register(FakeCommandProvider(listOf(aCommand("a", title = secretTitle, shortcut = shortcut))))
            registry.register(
                FakeCommandProvider(listOf(aCommand("a", title = secretTitle, keywords = listOf("SekretnyKlient")))),
            )
            registry.register(FakeCommandProvider(listOf(aCommand("b", title = secretTitle, shortcut = shortcut))))
            runCurrent()

            logger.entries.shouldNotBeEmpty()
            logger.entries.forEach { entry ->
                entry.message shouldNotContain "SekretnyKlient"
                entry.fields.toString() shouldNotContain "SekretnyKlient"
                entry.fields.values.none { it is String && it == secretTitle } shouldBe true
            }
        }
}
