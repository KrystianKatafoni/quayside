package dev.quayside.projects.impl

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.storage.BrokenFile
import dev.quayside.core.storage.InMemoryJsonStore
import dev.quayside.core.storage.StorageError
import dev.quayside.core.testing.LogEntry
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.testDispatchers
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.projects.api.StorageIssue
import dev.quayside.projects.api.StorageProblem
import dev.quayside.projects.api.aProject
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * [JsonProjectStore] over [InMemoryJsonStore]: start-up mapping, refused commands, failed saves,
 * the id source and what reaches the logs (AM-302 plan, decisions 3–7). Directories are real (the
 * store resolves `toRealPath()`), the list file is not.
 */
class JsonProjectStoreTest {
    @TempDir
    lateinit var tempDir: Path

    private val logger = RecordingLogger()
    private val store = InMemoryJsonStore(emptyList<Project>())

    /** Ids `p1`, `p2`, … unless a test gives its own sequence. */
    private var ids: Iterator<String> = generateSequence(1) { it + 1 }.map { "p$it" }.iterator()

    private fun dir(name: String): Path = Files.createDirectories(tempDir.toRealPath().resolve(name))

    private suspend fun TestScope.open(): JsonProjectStore =
        JsonProjectStore.open(store, testDispatchers().io, logger) { ProjectId(ids.next()) }

    private suspend fun ProjectsApi.added(
        dir: Path,
        displayName: String? = null,
    ): Project = add(dir, displayName).getOrNull().shouldNotBeNull()

    private suspend fun storedList(): List<Project> =
        store
            .load()
            .getOrNull()
            .shouldNotBeNull()
            .value

    // --- Start-up ---

    @Test
    fun `missing file starts empty without an issue`() =
        runTest {
            val api = open()

            api.projects.value.shouldBeEmpty()
            api.storageIssue.value shouldBe null
            store.saves shouldBe 0
        }

    @Test
    fun `stored projects are listed in stored order`() =
        runTest {
            val stored = listOf(aProject(id = "b", dir = Path.of("/z")), aProject(id = "a", dir = Path.of("/a")))
            store.save(stored)

            val api = open()

            api.projects.value shouldContainExactly stored
            api.storageIssue.value shouldBe null
        }

    @Test
    fun `recovered broken file reports RECOVERED and commands work`() =
        runTest {
            store.recoverNextLoad = BrokenFile(tempDir.resolve("projects.json.broken"), "invalid JSON")

            val api = open()

            api.storageIssue.value shouldBe StorageIssue.RECOVERED_FROM_BROKEN_FILE
            api.projects.value.shouldBeEmpty()
            val project = api.added(dir("a"))
            api.projects.value shouldContainExactly listOf(project)
            storedList() shouldContainExactly listOf(project)
            logger.entriesAt(LogLevel.WARN).shouldNotBeEmpty()
        }

    @Test
    fun `newer schema blocks every command with NEWER_SCHEMA`() =
        runTest {
            store.failure = StorageError.NewerSchema(found = 2, supported = 1)

            val api = open()

            api.storageIssue.value shouldBe StorageIssue.NEWER_SCHEMA
            api.projects.value.shouldBeEmpty()
            api.shouldRefuseEveryCommandWith(StorageProblem.NEWER_SCHEMA)
            logger.entriesAt(LogLevel.WARN).shouldNotBeEmpty()
        }

    @Test
    fun `read failure blocks every command with READ_FAILED`() =
        runTest {
            store.failure = StorageError.IoFailed(tempDir.resolve("projects.json"), "Permission denied")

            val api = open()

            api.storageIssue.value shouldBe StorageIssue.READ_FAILED
            api.projects.value.shouldBeEmpty()
            api.shouldRefuseEveryCommandWith(StorageProblem.READ_FAILED)
            logger.entriesAt(LogLevel.WARN).shouldNotBeEmpty()
        }

    @Test
    fun `blocked store never saves`() =
        runTest {
            store.failure = StorageError.IoFailed(tempDir.resolve("projects.json"), "Permission denied")
            val api = open()
            // The store would accept a save now; the start-up problem must still refuse every command.
            store.failure = null

            api.shouldRefuseEveryCommandWith(StorageProblem.READ_FAILED)

            store.saves shouldBe 0
            api.projects.value.shouldBeEmpty()
        }

    @Test
    fun `blocked store refuses before validating input`() =
        runTest {
            store.failure = StorageError.NewerSchema(found = 2, supported = 1)
            val api = open()

            api.add(Path.of("relative/path"), "   ") shouldBe
                Outcome.Err(ProjectError.StorageFailed(StorageProblem.NEWER_SCHEMA))
            api.rename(ProjectId("p1"), "   ") shouldBe
                Outcome.Err(ProjectError.StorageFailed(StorageProblem.NEWER_SCHEMA))
        }

    // --- Saving ---

    @Test
    fun `failed save returns WRITE_FAILED and keeps the list`() =
        runTest {
            val api = open()
            val kept = api.added(dir("kept"))
            store.failure = StorageError.IoFailed(tempDir.resolve("projects.json"), "No space left on device")

            api.add(dir("new")) shouldBe Outcome.Err(ProjectError.StorageFailed(StorageProblem.WRITE_FAILED))

            api.projects.value shouldContainExactly listOf(kept)
            store.failure = null
            storedList() shouldContainExactly listOf(kept)
            logger.entriesAt(LogLevel.WARN).shouldNotBeEmpty()
        }

    @Test
    fun `save rejected as newer schema returns WRITE_FAILED`() =
        runTest {
            val api = open()
            store.failure = StorageError.NewerSchema(found = 2, supported = 1)

            api.add(dir("new")) shouldBe Outcome.Err(ProjectError.StorageFailed(StorageProblem.WRITE_FAILED))

            api.projects.value.shouldBeEmpty()
        }

    @Test
    fun `failed save of remove and rename keeps the list`() =
        runTest {
            val api = open()
            val first = api.added(dir("first"))
            val second = api.added(dir("second"))
            store.failure = StorageError.IoFailed(tempDir.resolve("projects.json"), "No space left on device")

            api.remove(first.id) shouldBe Outcome.Err(ProjectError.StorageFailed(StorageProblem.WRITE_FAILED))
            api.rename(second.id, "Renamed") shouldBe
                Outcome.Err(ProjectError.StorageFailed(StorageProblem.WRITE_FAILED))

            api.projects.value shouldContainExactly listOf(first, second)
            store.failure = null
            storedList() shouldContainExactly listOf(first, second)
        }

    @Test
    fun `successful command saves the new list before returning`() =
        runTest {
            val api = open()

            val first = api.added(dir("first"))
            storedList() shouldContainExactly listOf(first)
            store.saves shouldBe 1

            val second = api.added(dir("second"))
            api.rename(first.id, "Renamed") shouldBe Outcome.Ok(Unit)
            storedList() shouldContainExactly listOf(first.copy(displayName = "Renamed"), second)
            store.saves shouldBe 3

            api.remove(first.id) shouldBe Outcome.Ok(Unit)
            storedList() shouldContainExactly listOf(second)
            api.projects.value shouldContainExactly listOf(second)
            store.saves shouldBe 4
        }

    @Test
    fun `rejected command does not save`() =
        runTest {
            val api = open()
            val project = api.added(dir("a"))

            api.add(dir("a")) shouldBe Outcome.Err(ProjectError.AlreadyAdded(project.id))
            api.remove(ProjectId("unknown")) shouldBe Outcome.Err(ProjectError.UnknownProject(ProjectId("unknown")))
            api.rename(project.id, " ").getOrNull() shouldBe null

            store.saves shouldBe 1
        }

    // --- Ids ---

    @Test
    fun `ids come from the id source`() =
        runTest {
            ids = listOf("first-id", "second-id").iterator()
            val api = open()

            api.added(dir("a")).id shouldBe ProjectId("first-id")
            api.added(dir("b")).id shouldBe ProjectId("second-id")
        }

    @Test
    fun `colliding id is drawn again`() =
        runTest {
            val existing = aProject(id = "p1", dir = Path.of("/stored"))
            store.save(listOf(existing))
            ids = listOf("p1", "p1", "p2").iterator()
            val api = open()

            val added = api.added(dir("a"))

            added.id shouldBe ProjectId("p2")
            api.projects.value shouldContainExactly listOf(existing, added)
        }

    @Test
    fun `id source that keeps colliding fails fast`() =
        runTest {
            store.save(listOf(aProject(id = "p1", dir = Path.of("/stored"))))
            ids = generateSequence { "p1" }.iterator()
            val api = open()

            shouldThrow<IllegalStateException> { api.add(dir("a"), null) }
        }

    // --- Logs (ENG-C8) ---

    @Test
    fun `logs contain project ids and never paths or names`() =
        runTest {
            val api = open()

            val project = api.added(dir("secret-user/project"), "secret-user project")
            api.rename(project.id, "secret-user renamed") shouldBe Outcome.Ok(Unit)
            api.remove(project.id) shouldBe Outcome.Ok(Unit)

            val info = logger.entriesAt(LogLevel.INFO)
            info.map { it.message } shouldContainExactly listOf("project added", "project renamed", "project removed")
            info.map { it.fields["projectId"].toString() } shouldContainExactly List(3) { project.id.value }
            logger.entries.forEach { it.text() shouldNotContain SECRET }
        }

    @Test
    fun `storage error reason never reaches errors or logs`() =
        runTest {
            val secretPath = Path.of("/Users/$SECRET/Library/Application Support/Quayside/projects.json")
            store.failure = StorageError.IoFailed(secretPath, "$secretPath: Permission denied ($SECRET)")
            val blocked = open()

            blocked.add(dir("a")).toString() shouldNotContain SECRET
            blocked.remove(ProjectId("p1")).toString() shouldNotContain SECRET

            store.failure = null
            store.recoverNextLoad = BrokenFile(secretPath.resolveSibling("projects.json.broken"), "invalid JSON")
            val recovered = open()
            store.failure = StorageError.IoFailed(secretPath, "$secretPath: No space left on device ($SECRET)")

            recovered.add(dir("b")).toString() shouldNotContain SECRET

            logger.entries.shouldNotBeEmpty()
            logger.entries.forEach { it.text() shouldNotContain SECRET }
        }

    private suspend fun ProjectsApi.shouldRefuseEveryCommandWith(problem: StorageProblem) {
        val refused = Outcome.Err(ProjectError.StorageFailed(problem))
        add(dir("blocked")) shouldBe refused
        add(dir("named"), "Named") shouldBe refused
        remove(ProjectId("p1")) shouldBe refused
        rename(ProjectId("p1"), "Name") shouldBe refused
        projects.value.shouldBeEmpty()
    }

    private fun LogEntry.text(): String = "$message $fields ${cause?.message}"

    private companion object {
        const val SECRET = "secret-user"
    }
}
