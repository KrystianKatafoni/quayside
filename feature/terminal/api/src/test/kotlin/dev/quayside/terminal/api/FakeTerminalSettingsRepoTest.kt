package dev.quayside.terminal.api

import dev.quayside.core.domain.errorOrNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Behaviour of [FakeTerminalSettingsRepo] beyond the contract: validation comes before the storage failure. */
class FakeTerminalSettingsRepoTest {
    @Test
    fun `invalid change reports Invalid even when storage fails`() =
        runTest {
            val repo = FakeTerminalSettingsRepo().apply { failure = true }

            val error = repo.update { it.copy(fontSize = 1) }.errorOrNull()

            error shouldBe TerminalSettingsError.Invalid(TerminalSettingsField.FONT_SIZE)
        }

    @Test
    fun `valid change with failing storage reports StorageFailed`() =
        runTest {
            val repo = FakeTerminalSettingsRepo().apply { failure = true }

            repo.update { it.copy(fontSize = 14) }.errorOrNull() shouldBe TerminalSettingsError.StorageFailed
            repo.saves shouldBe 0
        }
}
