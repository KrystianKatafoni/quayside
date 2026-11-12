package dev.quayside.accounts.impl

import dev.quayside.accounts.api.Account
import dev.quayside.accounts.api.CopyItem
import dev.quayside.accounts.api.NewAccount
import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** `AccountSetup.create` on a real file system: the new directory, its permissions and the `projects` link. */
class FileAccountSetupTest : AccountsFsTest() {
    private val noCopy = emptySet<CopyItem>()

    @BeforeEach
    fun posix() = AccountsEnv.assumePosix()

    private suspend fun create(request: NewAccount): Account {
        val api = env.open()
        return (api.setup.create(request) as Outcome.Ok).value
    }

    private val Account.projects: Path get() = configDir.resolve("projects")

    @Test
    fun `create returns the account in the quayside dir`() =
        runTest {
            val account = create(NewAccount("Praca", noCopy))

            account.configDir shouldBe env.home.resolve(".claude-quayside").resolve("praca")
            Files.isDirectory(account.configDir) shouldBe true
        }

    @Test
    fun `new dir is owner only`() =
        runTest {
            val account = create(NewAccount("Praca", noCopy))

            env.permissions(account.configDir) shouldBe "rwx------"
        }

    @Test
    fun `projects link points to the default projects dir`() =
        runTest {
            env.put(env.defaultDir, "projects/p/s.jsonl")

            val account = create(NewAccount("Praca", noCopy))

            Files.isSymbolicLink(account.projects) shouldBe true
            Files.readSymbolicLink(account.projects) shouldBe env.defaultDir.resolve("projects")
        }

    @Test
    fun `transcript written through the link lands in the default projects dir`() =
        runTest {
            Files.createDirectories(env.defaultDir.resolve("projects"))
            val account = create(NewAccount("Praca", noCopy))

            env.put(account.projects, "p/new.jsonl", "line")

            env.read(env.defaultDir.resolve("projects/p/new.jsonl")) shouldBe "line"
        }

    @Test
    fun `missing default projects dir is created`() =
        runTest {
            Files.exists(env.defaultDir.resolve("projects")) shouldBe false

            val account = create(NewAccount("Praca", noCopy))

            Files.isDirectory(env.defaultDir.resolve("projects"), NOFOLLOW_LINKS) shouldBe true
            Files.isDirectory(account.projects) shouldBe true
        }

    @Test
    fun `shareProjects false creates no link`() =
        runTest {
            Files.createDirectories(env.defaultDir.resolve("projects"))

            val account = create(NewAccount("Praca", noCopy, shareProjects = false))

            Files.exists(account.projects, NOFOLLOW_LINKS) shouldBe false
            env.names(account.configDir) shouldBe emptySet()
        }

    @Test
    fun `the created account is stored for the next start`() =
        runTest {
            create(NewAccount("Praca", noCopy))

            env
                .open()
                .repo.state.value.accounts
                .map { it.id.value } shouldBe listOf("default", "praca")
        }
}
