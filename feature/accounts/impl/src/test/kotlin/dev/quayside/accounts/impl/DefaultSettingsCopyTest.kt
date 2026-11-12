package dev.quayside.accounts.impl

import dev.quayside.accounts.api.Account
import dev.quayside.accounts.api.CopyItem
import dev.quayside.accounts.api.NewAccount
import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files

/** The one-time copy from `~/.claude` while an account is created: which items, and which never (ADR 0035 p.5). */
class DefaultSettingsCopyTest : AccountsFsTest() {
    private suspend fun create(request: NewAccount): Account = (env.open().setup.create(request) as Outcome.Ok).value

    @Test
    fun `copies only the chosen items`() =
        runTest {
            env.fillDefault()

            val dir = create(NewAccount("Praca", setOf(CopyItem.SKILLS, CopyItem.INSTRUCTIONS), false)).configDir

            env.names(dir) shouldBe setOf("CLAUDE.md", "skills")
            env.read(dir.resolve("CLAUDE.md")) shouldBe "instructions"
            env.read(dir.resolve("skills/deploy/SKILL.md")) shouldBe "skill"
        }

    @Test
    fun `copies each of the five items`() =
        runTest {
            env.fillDefault()

            val dir = create(NewAccount("Praca", CopyItem.entries.toSet(), false)).configDir

            env.names(dir) shouldBe setOf("CLAUDE.md", "settings.json", "agents", "skills", "commands")
            env.read(dir.resolve("settings.json")) shouldBe "{}"
            env.read(dir.resolve("agents/reviewer.md")) shouldBe "agent"
            env.read(dir.resolve("commands/ship.md")) shouldBe "command"
        }

    @Test
    fun `never copies plugins, claude json, credentials, daemon or jobs`() =
        runTest {
            env.fillDefault()

            val dir = create(NewAccount("Praca")).configDir

            env.names(dir) shouldBe setOf("CLAUDE.md", "settings.json", "agents", "skills", "commands", "projects")
        }

    @Test
    fun `an empty choice copies nothing`() =
        runTest {
            env.fillDefault()

            val dir = create(NewAccount("Praca", emptySet(), false)).configDir

            env.names(dir) shouldBe emptySet()
        }

    @Test
    fun `a missing item is skipped and the rest is copied`() =
        runTest {
            env.put(env.defaultDir, "CLAUDE.md", "instructions")

            val dir = create(NewAccount("Praca", setOf(CopyItem.INSTRUCTIONS, CopyItem.AGENTS), false)).configDir

            env.names(dir) shouldBe setOf("CLAUDE.md")
        }

    @Test
    fun `symlink inside skills is copied as a link`() =
        runTest {
            AccountsEnv.assumePosix()
            env.fillDefault()
            val shared = env.put(env.home, "shared/SKILL.md", "shared")
            val link = Files.createSymbolicLink(env.defaultDir.resolve("skills/linked"), shared.parent)

            val dir = create(NewAccount("Praca", setOf(CopyItem.SKILLS), false)).configDir

            val copied = dir.resolve("skills/linked")
            Files.isSymbolicLink(copied) shouldBe true
            Files.readSymbolicLink(copied) shouldBe Files.readSymbolicLink(link)
            Files.isSymbolicLink(env.defaultDir.resolve("skills/linked")) shouldBe true
        }

    @Test
    fun `the default account is left as it was`() =
        runTest {
            env.fillDefault()
            val before = env.names(env.defaultDir)

            create(NewAccount("Praca"))

            env.names(env.defaultDir) shouldBe before
            env.read(env.defaultDir.resolve("CLAUDE.md")) shouldBe "instructions"
        }
}
