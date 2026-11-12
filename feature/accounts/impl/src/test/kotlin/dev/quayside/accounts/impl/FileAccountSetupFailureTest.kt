package dev.quayside.accounts.impl

import dev.quayside.accounts.api.AccountSetupError
import dev.quayside.accounts.api.AccountsError
import dev.quayside.accounts.api.NewAccount
import dev.quayside.accounts.api.SetupStage
import dev.quayside.accounts.api.defaultState
import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/** What `AccountSetup.create` leaves behind when a step fails: nothing (ADR 0035 p.5). */
class FileAccountSetupFailureTest : AccountsFsTest() {
    private val target get() = env.home.resolve(".claude-quayside").resolve("praca")
    private val locked get() = env.defaultDir.resolve("skills/locked.md")

    @BeforeEach
    fun posix() = AccountsEnv.assumePosixNotRoot()

    @AfterEach
    fun unlock() {
        if (Files.exists(locked)) Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rw-------"))
    }

    /** The unreadable file is set after the guards (it is a regular file) and fails only when copied. */
    private fun lockOneSkill() {
        env.fillDefault()
        env.put(env.defaultDir, "skills/locked.md", "x")
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"))
    }

    @Test
    fun `copy failing midway removes the new dir and registers nothing`() =
        runTest {
            lockOneSkill()
            val api = env.open()

            val result = api.setup.create(NewAccount("Praca"))

            result shouldBe Outcome.Err(AccountSetupError.DiskFailed(SetupStage.COPY))
            Files.exists(target) shouldBe false
            api.repo.state.value shouldBe defaultState(env.home)
            Files.exists(env.file) shouldBe false
        }

    @Test
    fun `cleanup never deletes through the projects link`() =
        runTest {
            lockOneSkill()

            env.open().setup.create(NewAccount("Praca"))

            env.read(env.defaultDir.resolve("projects/p/s.jsonl")) shouldBe "transcript"
            env.read(env.defaultDir.resolve("CLAUDE.md")) shouldBe "instructions"
        }

    @Test
    fun `registry failure removes the new dir`() =
        runTest {
            env.fillDefault()
            val api = env.open()
            env.breakRegistry()

            val result = api.setup.create(NewAccount("Praca"))

            result shouldBe Outcome.Err(AccountSetupError.Registry(AccountsError.WriteFailed))
            Files.exists(target) shouldBe false
            env.read(env.defaultDir.resolve("projects/p/s.jsonl")) shouldBe "transcript"
        }

    @Test
    fun `existing target dir is AlreadyExists and stays untouched`() =
        runTest {
            env.fillDefault()
            env.put(target, "keep.txt", "mine")
            val api = env.open()

            val result = api.setup.create(NewAccount("Praca"))

            result shouldBe Outcome.Err(AccountSetupError.AlreadyExists)
            env.names(target) shouldBe setOf("keep.txt")
            env.read(target.resolve("keep.txt")) shouldBe "mine"
            api.repo.state.value shouldBe defaultState(env.home)
        }

    @Test
    fun `a failed create does not remove the quayside dir of other accounts`() =
        runTest {
            lockOneSkill()
            env.put(env.home.resolve(".claude-quayside/inne"), "CLAUDE.md", "other")

            env.open().setup.create(NewAccount("Praca"))

            env.read(env.home.resolve(".claude-quayside/inne/CLAUDE.md")) shouldBe "other"
        }
}
