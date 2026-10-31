package dev.quayside.terminal.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Behaviour every [TerminalSettingsRepo] must have (ENG-T2). Runs on the fake and on the JSON implementation. */
public abstract class TerminalSettingsRepoContract {
    /** A fresh repo with nothing stored. Called once per test. */
    protected abstract suspend fun create(): TerminalSettingsRepo

    @Test
    public fun `starts with defaults`(): Unit =
        runTest {
            val repo = create()

            repo.settings.value shouldBe TerminalSettings()
            repo.settings.value.optionAsMeta shouldBe false
            repo.issue.value shouldBe null
        }

    @Test
    public fun `update publishes the saved settings`(): Unit =
        runTest {
            val repo = create()

            val result = repo.update { it.copy(shell = ShellProfile.ZSH, fontSize = 16, ligatures = false) }

            val saved = TerminalSettings(shell = ShellProfile.ZSH, fontSize = 16, ligatures = false)
            result shouldBe Outcome.Ok(saved)
            repo.settings.value shouldBe saved
        }

    @Test
    public fun `invalid update returns the field and keeps settings`(): Unit =
        runTest {
            val repo = create()
            repo.update { it.copy(fontSize = 20) }

            val result = repo.update { it.copy(fontSize = 7) }

            result shouldBe Outcome.Err(TerminalSettingsError.Invalid(TerminalSettingsField.FONT_SIZE))
            repo.settings.value shouldBe TerminalSettings(fontSize = 20)
        }

    @Test
    public fun `blank font family is saved as null`(): Unit =
        runTest {
            val repo = create()

            val result = repo.update { it.copy(fontFamily = "  ") }

            result shouldBe Outcome.Ok(TerminalSettings(fontFamily = null))
            repo.settings.value.fontFamily shouldBe null
        }

    @Test
    public fun `concurrent updates lose no change`(): Unit =
        runTest {
            val repo = create()

            List(UPDATES) { async { repo.update { it.copy(fontSize = it.fontSize + 1) } } }.awaitAll()

            repo.settings.value.fontSize shouldBe TerminalSettings().fontSize + UPDATES
        }

    private companion object {
        const val UPDATES = 10
    }
}
