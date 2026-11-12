package dev.quayside.accounts.impl

import dev.quayside.accounts.api.AccountSetupError
import dev.quayside.accounts.api.CopyItem
import dev.quayside.accounts.api.CopyReport
import dev.quayside.accounts.api.SetupStage
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/** `copyFromDefault` into an account that exists: merge by path, back up what is overwritten (ADR 0035 p.7). */
class AccountBackupsTest : AccountsFsTest() {
    private val work get() = env.home.resolve("work")

    /** The account `work` is a registered existing directory with its own CLAUDE.md "old". */
    private suspend fun setup() =
        env.open().setup.also {
            env.put(work, "CLAUDE.md", "old")
            it.register("Work", work)
        }

    private suspend fun copy(
        items: Set<CopyItem>,
        id: String = "work",
    ): CopyReport = (setup().copyFromDefault(AccountId(id), items) as Outcome.Ok).value

    private fun backupOf(report: CopyReport): Path = report.backupDir ?: error("no backup was made")

    @Test
    fun `overwritten file is backed up with its old content`() =
        runTest {
            env.fillDefault()
            env.put(work, "skills/deploy/SKILL.md", "old skill")

            val report = copy(setOf(CopyItem.INSTRUCTIONS, CopyItem.SKILLS))

            val backup = backupOf(report)
            backup.fileName.toString() shouldBe "${env.clock.now().toEpochMilli()}-work"
            backup.parent shouldBe env.backups
            env.read(backup.resolve("CLAUDE.md")) shouldBe "old"
            env.read(backup.resolve("skills/deploy/SKILL.md")) shouldBe "old skill"
            env.read(work.resolve("CLAUDE.md")) shouldBe "instructions"
            env.read(work.resolve("skills/deploy/SKILL.md")) shouldBe "skill"
            report.copied shouldBe setOf(CopyItem.INSTRUCTIONS, CopyItem.SKILLS)
        }

    @Test
    fun `identical file is neither backed up nor rewritten`() =
        runTest {
            env.put(env.defaultDir, "CLAUDE.md", "same")
            val setup = setup()
            env.put(work, "CLAUDE.md", "same")
            val past = FileTime.fromMillis(1_000_000_000_000)
            Files.setLastModifiedTime(work.resolve("CLAUDE.md"), past)

            val report = (setup.copyFromDefault(AccountId("work"), setOf(CopyItem.INSTRUCTIONS)) as Outcome.Ok).value

            report.backupDir shouldBe null
            env.mtime(work.resolve("CLAUDE.md")) shouldBe past
            Files.exists(env.backups) shouldBe false
        }

    @Test
    fun `only the file with other content is backed up`() =
        runTest {
            env.fillDefault()
            env.put(work, "skills/deploy/SKILL.md", "skill")
            env.put(work, "skills/deploy/notes.md", "kept")

            val backup = backupOf(copy(setOf(CopyItem.INSTRUCTIONS, CopyItem.SKILLS)))

            env.names(backup) shouldBe setOf("CLAUDE.md")
        }

    @Test
    fun `extra files of the account stay`() =
        runTest {
            env.fillDefault()
            env.put(work, "skills/own/SKILL.md", "own")
            env.put(work, "skills/deploy/notes.md", "notes")

            copy(setOf(CopyItem.SKILLS))

            env.read(work.resolve("skills/own/SKILL.md")) shouldBe "own"
            env.read(work.resolve("skills/deploy/notes.md")) shouldBe "notes"
            env.read(work.resolve("skills/deploy/SKILL.md")) shouldBe "skill"
        }

    @Test
    fun `failing backup overwrites nothing`() =
        runTest {
            env.fillDefault()
            val setup = setup()
            Files.createDirectories(env.support)
            Files.writeString(env.backups, "i am a file")

            val result = setup.copyFromDefault(AccountId("work"), setOf(CopyItem.INSTRUCTIONS, CopyItem.AGENTS))

            result shouldBe Outcome.Err(AccountSetupError.DiskFailed(SetupStage.BACKUP))
            env.read(work.resolve("CLAUDE.md")) shouldBe "old"
            env.names(work) shouldBe setOf("CLAUDE.md")
        }

    @Test
    fun `backup file is owner only`() =
        runTest {
            AccountsEnv.assumePosix()
            env.fillDefault()

            val backup = backupOf(copy(setOf(CopyItem.INSTRUCTIONS)))

            env.permissions(backup.resolve("CLAUDE.md")) shouldBe "rw-------"
        }

    @Test
    fun `nothing overwritten gives null backupDir`() =
        runTest {
            env.fillDefault()
            val setup = setup()
            Files.delete(work.resolve("CLAUDE.md"))

            val report =
                (
                    setup.copyFromDefault(
                        AccountId("work"),
                        setOf(CopyItem.INSTRUCTIONS, CopyItem.AGENTS),
                    ) as Outcome.Ok
                ).value

            report.backupDir shouldBe null
            report.copied shouldBe setOf(CopyItem.INSTRUCTIONS, CopyItem.AGENTS)
            Files.exists(env.backups) shouldBe false
        }

    @Test
    fun `missing item on the default account is reported as skipped`() =
        runTest {
            env.put(env.defaultDir, "CLAUDE.md", "instructions")

            val report = copy(setOf(CopyItem.INSTRUCTIONS, CopyItem.AGENTS, CopyItem.SETTINGS))

            report.copied shouldBe setOf(CopyItem.INSTRUCTIONS)
            report.skipped shouldBe setOf(CopyItem.AGENTS, CopyItem.SETTINGS)
        }

    @Test
    fun `copy never touches the default account`() =
        runTest {
            env.fillDefault()
            val namesBefore = env.names(env.defaultDir)

            copy(CopyItem.entries.toSet())

            env.read(env.defaultDir.resolve("CLAUDE.md")) shouldBe "instructions"
            env.names(env.defaultDir) shouldBe namesBefore
        }

    @Test
    fun `a directory at an overwritten path is BACKUP and writes nothing`() =
        runTest {
            env.fillDefault()
            val setup = setup()
            Files.delete(work.resolve("CLAUDE.md"))
            env.put(work, "CLAUDE.md/inside", "blocks the move")

            val result = setup.copyFromDefault(AccountId("work"), setOf(CopyItem.INSTRUCTIONS))

            result shouldBe Outcome.Err(AccountSetupError.DiskFailed(SetupStage.BACKUP))
            Files.exists(env.backups) shouldBe false
            env.names(work) shouldBe setOf("CLAUDE.md")
            env.names(work.resolve("CLAUDE.md")) shouldBe setOf("inside")
            env.read(work.resolve("CLAUDE.md/inside")) shouldBe "blocks the move"
        }

    @Test
    fun `place leaves no temp file when the move fails`() {
        val source = env.put(env.home, "source.txt", "new")
        val dest = env.put(env.home, "target/dest/inside", "old").parent
        val target = dest.parent

        shouldThrow<IOException> { AccountDirectories.place(source, dest, ownerOnly = false) }

        env.names(target) shouldBe setOf("dest")
        env.read(dest.resolve("inside")) shouldBe "old"
    }
}
