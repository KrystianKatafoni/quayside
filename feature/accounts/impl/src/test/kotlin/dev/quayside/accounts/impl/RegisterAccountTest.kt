package dev.quayside.accounts.impl

import dev.quayside.accounts.api.AccountSetupError
import dev.quayside.accounts.api.defaultState
import dev.quayside.core.domain.AccountId
import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** `AccountSetup.register` on a real file system: it only records the directory (ADR 0035 p.6). */
class RegisterAccountTest : AccountsFsTest() {
    private fun existing(name: String = "work"): Path {
        val dir = env.home.resolve(name)
        env.put(dir, "CLAUDE.md", "mine")
        env.put(dir, "skills/own/SKILL.md", "own")
        return dir
    }

    private fun snapshot(dir: Path): Map<String, Any> =
        Files.walk(dir).use { walk ->
            walk.toList().associate { it.toString() to (if (Files.isRegularFile(it)) env.read(it) else "dir") }
        }

    @Test
    fun `register writes nothing into the dir`() =
        runTest {
            env.fillDefault()
            val dir = existing()
            val before = snapshot(dir)
            val mtime = env.mtime(dir)

            val result = env.open().setup.register("Work", dir)

            (result is Outcome.Ok) shouldBe true
            snapshot(dir) shouldBe before
            env.mtime(dir) shouldBe mtime
        }

    @Test
    fun `register keeps the account for the next start`() =
        runTest {
            val dir = existing()
            env.open().setup.register("Work", dir)

            val accounts =
                env
                    .open()
                    .repo.state.value.accounts

            accounts.map { it.id } shouldBe listOf(AccountId.DEFAULT, AccountId("work"))
            accounts.last().configDir shouldBe dir
            accounts.last().label shouldBe "Work"
        }

    @Test
    fun `register of the default dir with a trailing dot is DefaultDirectory`() =
        runTest {
            val api = env.open()

            api.setup.register("Domyslne", env.defaultDir.resolve(".")) shouldBe
                Outcome.Err(AccountSetupError.DefaultDirectory)
            api.repo.state.value shouldBe defaultState(env.home)
        }

    @Test
    fun `register of the default dir through a dot dot path is DefaultDirectory`() =
        runTest {
            val api = env.open()
            Files.createDirectories(env.home.resolve("sub"))

            api.setup.register(
                "Domyslne",
                env.home
                    .resolve("sub")
                    .resolve("..")
                    .resolve(".claude"),
            ) shouldBe
                Outcome.Err(AccountSetupError.DefaultDirectory)
        }

    @Test
    fun `register of the default dir through a symlink is DefaultDirectory`() =
        runTest {
            AccountsEnv.assumePosix()
            val link = Files.createSymbolicLink(env.home.resolve("alias"), env.defaultDir)

            env.open().setup.register("Alias", link) shouldBe Outcome.Err(AccountSetupError.DefaultDirectory)
        }

    @Test
    fun `register of a file is NotADirectory`() =
        runTest {
            val file = env.put(env.home, "plik.txt")

            env.open().setup.register("Plik", file) shouldBe Outcome.Err(AccountSetupError.NotADirectory)
        }

    @Test
    fun `register of a missing path is NotADirectory`() =
        runTest {
            env.open().setup.register("Brak", env.home.resolve("nie-ma")) shouldBe
                Outcome.Err(AccountSetupError.NotADirectory)
        }

    @Test
    fun `register of a name with no usable letters is InvalidName and writes nothing`() =
        runTest {
            val dir = existing()
            val before = snapshot(dir)

            env.open().setup.register("!!!", dir) shouldBe Outcome.Err(AccountSetupError.InvalidName)

            snapshot(dir) shouldBe before
        }

    @Test
    fun `register of a second dir with the same slug is AlreadyExists`() =
        runTest {
            val api = env.open()
            api.setup.register("Work", existing("a"))

            api.setup.register("work", existing("b")) shouldBe Outcome.Err(AccountSetupError.AlreadyExists)
        }
}
