package dev.agentmanager.projects.impl

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.ProjectId
import dev.agentmanager.core.domain.getOrNull
import dev.agentmanager.core.storage.JsonStorage
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.projects.api.Project
import dev.agentmanager.projects.api.ProjectError
import dev.agentmanager.projects.api.ProjectsApi
import dev.agentmanager.projects.api.StorageIssue
import dev.agentmanager.projects.api.StorageProblem
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.paths.shouldBeADirectory
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.Executors
import kotlin.io.path.deleteExisting
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

/**
 * `projectsModule` on a real [JsonStorage] in a temp directory (docs/12: adapters on local
 * resources). Blocking file IO runs on a real thread pool; nothing waits on wall-clock time.
 */
class JsonProjectStoreIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    // Real threads: JsonStorage blocks on file IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    private val logger = RecordingLogger()

    @AfterEach
    fun closeDispatcher() = io.close()

    private val storageDir: Path get() = tempDir.resolve("storage")
    private val file: Path get() = storageDir.resolve(FILE)
    private val broken: Path get() = storageDir.resolve("$FILE.broken")

    private fun dir(name: String): Path = Files.createDirectories(tempDir.toRealPath().resolve("workspace/$name"))

    private suspend fun open(): ProjectsApi = projectsModule(JsonStorage(storageDir, io), io, logger)

    private suspend fun ProjectsApi.added(
        dir: Path,
        displayName: String? = null,
    ): Project = add(dir, displayName).getOrNull().shouldNotBeNull()

    private fun writeFile(content: String): ByteArray {
        Files.createDirectories(storageDir)
        file.writeText(content)
        return file.readBytes()
    }

    @Test
    fun `projects survive reopening the store`() =
        runTest {
            val api = open()
            val zeta = api.added(dir("zeta"))
            val alpha = api.added(dir("alpha"), "Alpha")
            val mid = api.added(dir("mid"))
            api.rename(alpha.id, "Alpha renamed") shouldBe Outcome.Ok(Unit)
            api.remove(zeta.id) shouldBe Outcome.Ok(Unit)

            val reopened = open()

            reopened.projects.value shouldContainExactly listOf(alpha.copy(displayName = "Alpha renamed"), mid)
            reopened.storageIssue.value shouldBe null
        }

    @Test
    fun `file has schema version 1 and the documented layout`() =
        runTest {
            val dir = dir("agent-manager")
            val project = open().added(dir, "Agent Manager")

            val root = Json.parseToJsonElement(Files.readString(file)).jsonObject

            root.keys shouldBe setOf("schemaVersion", "data")
            root.getValue("schemaVersion").jsonPrimitive.int shouldBe 1
            val data = root.getValue("data").jsonObject
            data.keys shouldBe setOf("projects")
            data.getValue("projects").jsonArray.single() shouldBe
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(project.id.value),
                        "dir" to JsonPrimitive(dir.toString()),
                        "displayName" to JsonPrimitive("Agent Manager"),
                    ),
                )
        }

    @Test
    fun `unparseable file is moved to broken and the list starts empty`() =
        runTest {
            val original = writeFile("{ not json")

            val api = open()

            api.storageIssue.value shouldBe StorageIssue.RECOVERED_FROM_BROKEN_FILE
            api.projects.value.shouldBeEmpty()
            broken.readBytes() shouldBe original
            file.shouldNotExist()

            val project = api.added(dir("a"))
            file.shouldExist()
            open().projects.value shouldContainExactly listOf(project)
            broken.readBytes() shouldBe original
        }

    @Test
    fun `file with an invalid entry is moved to broken`() =
        runTest {
            val original =
                writeFile(
                    """{"schemaVersion": 1, "data": {"projects": [
                        {"id": "p1", "dir": "/valid", "displayName": "Valid"},
                        {"id": "p2", "dir": "rel/a", "displayName": "Relative"}
                    ]}}""",
                )

            val api = open()

            api.storageIssue.value shouldBe StorageIssue.RECOVERED_FROM_BROKEN_FILE
            api.projects.value.shouldBeEmpty()
            broken.readBytes() shouldBe original
        }

    @Test
    fun `newer schema file is never overwritten`() =
        runTest {
            val original = writeFile("""{"schemaVersion": 2, "data": {"projects": [], "groups": []}}""")

            val api = open()

            api.storageIssue.value shouldBe StorageIssue.NEWER_SCHEMA
            api.shouldRefuseEveryCommandWith(StorageProblem.NEWER_SCHEMA)
            file.readBytes() shouldBe original
            storageDir.listDirectoryEntries().map { it.fileName.toString() } shouldBe listOf(FILE)
        }

    @Test
    fun `unreadable file reports READ_FAILED and is never overwritten`() =
        runTest {
            // A directory where the file should be: reading fails deterministically, also as root.
            Files.createDirectories(file)

            val api = open()

            api.storageIssue.value shouldBe StorageIssue.READ_FAILED
            api.projects.value.shouldBeEmpty()
            api.shouldRefuseEveryCommandWith(StorageProblem.READ_FAILED)
            file.shouldBeADirectory()
            file.listDirectoryEntries().shouldBeEmpty()
            broken.shouldNotExist()
        }

    @Test
    fun `file without read permission reports READ_FAILED and is never overwritten`() =
        runTest {
            assumeTrue(isPosix())
            val original = writeFile("""{"schemaVersion": 1, "data": {"projects": []}}""")
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"))
            try {
                assumeFalse(Files.isReadable(file), "running as root: permissions are not enforced")

                val api = open()

                api.storageIssue.value shouldBe StorageIssue.READ_FAILED
                api.shouldRefuseEveryCommandWith(StorageProblem.READ_FAILED)
            } finally {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
            }
            file.readBytes() shouldBe original
            broken.shouldNotExist()
        }

    @Test
    fun `write failure returns WRITE_FAILED and the old file stays`() =
        runTest {
            assumeTrue(isPosix())
            val api = open()
            val kept = api.added(dir("kept"))
            val original = file.readBytes()
            Files.setPosixFilePermissions(storageDir, PosixFilePermissions.fromString("r-x------"))
            try {
                assumeFalse(Files.isWritable(storageDir), "running as root: permissions are not enforced")

                api.add(dir("new")) shouldBe Outcome.Err(ProjectError.StorageFailed(StorageProblem.WRITE_FAILED))
                api.rename(kept.id, "Renamed") shouldBe
                    Outcome.Err(ProjectError.StorageFailed(StorageProblem.WRITE_FAILED))
                api.remove(kept.id) shouldBe Outcome.Err(ProjectError.StorageFailed(StorageProblem.WRITE_FAILED))

                api.projects.value shouldContainExactly listOf(kept)
            } finally {
                Files.setPosixFilePermissions(storageDir, PosixFilePermissions.fromString("rwx------"))
            }
            file.readBytes() shouldBe original
            open().projects.value shouldContainExactly listOf(kept)
        }

    @Test
    fun `stored dir of a since deleted directory is still listed`() =
        runTest {
            val dir = dir("gone")
            val project = open().added(dir)
            dir.deleteExisting()

            val reopened = open()

            reopened.projects.value shouldContainExactly listOf(project)
            reopened.storageIssue.value shouldBe null
        }

    @Test
    fun `every added project gets a distinct id`() =
        runTest {
            val api = open()

            val ids = listOf(dir("a"), dir("b"), dir("c")).map { api.added(it).id }

            ids.toSet() shouldHaveSize 3
        }

    private suspend fun ProjectsApi.shouldRefuseEveryCommandWith(problem: StorageProblem) {
        val refused = Outcome.Err(ProjectError.StorageFailed(problem))
        add(dir("blocked")) shouldBe refused
        remove(ProjectId("p1")) shouldBe refused
        rename(ProjectId("p1"), "Name") shouldBe refused
        projects.value.shouldBeEmpty()
    }

    private fun isPosix(): Boolean = "posix" in FileSystems.getDefault().supportedFileAttributeViews()

    private companion object {
        const val FILE = "projects.json"
    }
}
