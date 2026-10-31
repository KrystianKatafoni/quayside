package dev.quayside.terminal.impl

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.storage.BrokenFile
import dev.quayside.core.storage.InMemoryJsonStore
import dev.quayside.core.storage.StorageError
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.terminal.api.TerminalSettings
import dev.quayside.terminal.api.TerminalSettingsError
import dev.quayside.terminal.api.TerminalSettingsField
import dev.quayside.terminal.api.TerminalSettingsIssue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** The repo over [InMemoryJsonStore]: what it does with a recovered, newer or unreadable file, and its logs. */
class JsonTerminalSettingsRepoTest {
    private val logger = RecordingLogger()
    private val file = Path.of("/data/terminal.json")

    @Test
    fun `stored settings are published at start`() =
        runTest {
            val stored = TerminalSettings(fontSize = 20, optionAsMeta = true)

            val repo = JsonTerminalSettingsRepo.open(InMemoryJsonStore(stored), logger)

            repo.settings.value shouldBe stored
            repo.issue.value shouldBe null
        }

    @Test
    fun `broken file gives defaults and the recovered issue`() =
        runTest {
            val store = InMemoryJsonStore(TerminalSettings())
            store.recoverNextLoad = BrokenFile(file.resolveSibling("terminal.json.broken"), "unparseable")

            val repo = JsonTerminalSettingsRepo.open(store, logger)

            repo.settings.value shouldBe TerminalSettings()
            repo.issue.value shouldBe TerminalSettingsIssue.RECOVERED_FROM_BROKEN_FILE
            repo.update { it.copy(fontSize = 14) } shouldBe Outcome.Ok(TerminalSettings(fontSize = 14))
        }

    @Test
    fun `newer schema refuses updates and keeps the file`() =
        runTest {
            val store = InMemoryJsonStore(TerminalSettings())
            store.failure = StorageError.NewerSchema(found = 2, supported = 1)

            val repo = JsonTerminalSettingsRepo.open(store, logger)

            repo.issue.value shouldBe TerminalSettingsIssue.NEWER_SCHEMA
            repo.settings.value shouldBe TerminalSettings()
            repo.update { it.copy(fontSize = 14) } shouldBe Outcome.Err(TerminalSettingsError.StorageFailed)
            store.saves shouldBe 0
        }

    @Test
    fun `unreadable file gives defaults and the read failed issue`() =
        runTest {
            val store = InMemoryJsonStore(TerminalSettings())
            store.failure = StorageError.IoFailed(file, "denied")

            val repo = JsonTerminalSettingsRepo.open(store, logger)

            repo.issue.value shouldBe TerminalSettingsIssue.READ_FAILED
            repo.settings.value shouldBe TerminalSettings()
            repo.update { it.copy(fontSize = 14) } shouldBe Outcome.Err(TerminalSettingsError.StorageFailed)
            store.saves shouldBe 0
        }

    @Test
    fun `failed write keeps the settings and logs the category`() =
        runTest {
            val store = InMemoryJsonStore(TerminalSettings())
            val repo = JsonTerminalSettingsRepo.open(store, logger)
            store.failure = StorageError.IoFailed(file, "disk full")

            val result = repo.update { it.copy(fontSize = 14) }

            result shouldBe Outcome.Err(TerminalSettingsError.StorageFailed)
            repo.settings.value shouldBe TerminalSettings()
            logger.entriesAt(LogLevel.WARN).flatMap { it.fields.values } shouldContain "storageFailed"
        }

    @Test
    fun `invalid update saves nothing`() =
        runTest {
            val store = InMemoryJsonStore(TerminalSettings())
            val repo = JsonTerminalSettingsRepo.open(store, logger)

            val result = repo.update { it.copy(scrollbackLines = 10) }

            result shouldBe Outcome.Err(TerminalSettingsError.Invalid(TerminalSettingsField.SCROLLBACK_LINES))
            store.saves shouldBe 0
        }

    @Test
    fun `save logs no values`() =
        runTest {
            val repo = JsonTerminalSettingsRepo.open(InMemoryJsonStore(TerminalSettings()), logger)

            repo.update { it.copy(fontFamily = "SecretFontName", fontSize = 27, scrollbackLines = 12_345) }

            logger.entriesAt(LogLevel.INFO).map { it.message } shouldContain "terminal settings saved"
            val logged = logger.entries.joinToString { "${it.message} ${it.fields} ${it.cause}" }
            logged shouldNotContain "SecretFontName"
            logged shouldNotContain "27"
            logged shouldNotContain "12345"
        }
}
