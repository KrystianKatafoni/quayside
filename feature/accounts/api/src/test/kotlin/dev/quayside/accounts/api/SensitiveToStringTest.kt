package dev.quayside.accounts.api

import dev.quayside.core.domain.AccountId
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** Names and paths of the user never reach logs or messages through `toString` (ENG-C8). */
class SensitiveToStringTest {
    private val label = "Sekretna-Etykieta"
    private val dir = Path.of("/Users/jan-kowalski/.claude-quayside/sekret")

    @Test
    fun `Account hides the label and the directory`() {
        val text = Account(AccountId("praca"), label, dir).toString()

        text shouldNotContain label
        text shouldNotContain "jan-kowalski"
        text shouldNotContain "sekret"
    }

    @Test
    fun `NewAccount hides the label`() {
        NewAccount(label).toString() shouldNotContain label
    }

    @Test
    fun `CopyReport hides the backup directory`() {
        CopyReport(setOf(CopyItem.SKILLS), emptySet(), dir).toString() shouldNotContain "jan-kowalski"
    }

    @Test
    fun `AccountPaths hides the home directory`() {
        AccountPaths(dir, dir.resolve("backup")).toString() shouldNotContain "jan-kowalski"
    }
}
