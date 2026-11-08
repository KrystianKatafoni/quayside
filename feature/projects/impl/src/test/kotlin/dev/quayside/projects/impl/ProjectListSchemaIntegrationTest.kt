package dev.quayside.projects.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.storage.JsonStorage
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.ProjectsApi
import dev.quayside.projects.api.StorageIssue
import dev.quayside.projects.api.StorageProblem
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.paths.shouldNotExist
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

/**
 * Schema 2 of `projects.json` (ADR 0026, FR-P7) through `projectsModule` on a real [JsonStorage] in a temp
 * directory. The loader and the serializer are the production ones, so a guard that only an in-memory store would
 * keep alive cannot hide here (Lessons).
 */
class ProjectListSchemaIntegrationTest {
    @TempDir
    lateinit var tempDir: Path

    // Real threads: JsonStorage and the directory checks block on file IO, which virtual time cannot drive.
    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()

    @AfterEach
    fun closeDispatcher() = io.close()

    private val storageDir: Path get() = tempDir.resolve("storage")
    private val file: Path get() = storageDir.resolve(FILE)
    private val broken: Path get() = storageDir.resolve("$FILE.broken")

    private fun dir(name: String): Path = Files.createDirectories(tempDir.toRealPath().resolve("workspace/$name"))

    private suspend fun open(): ProjectsApi = projectsModule(JsonStorage(storageDir, io), io, RecordingLogger())

    private suspend fun ProjectsApi.added(dir: Path): Project = add(dir).getOrNull().shouldNotBeNull()

    private fun writeFile(content: String): ByteArray {
        Files.createDirectories(storageDir)
        file.writeText(content)
        return file.readBytes()
    }

    private fun stored(): JsonObject = Json.parseToJsonElement(Files.readString(file)).jsonObject

    private fun storedEntries(): List<JsonObject> =
        stored()
            .getValue("data")
            .jsonObject
            .getValue("projects")
            .jsonArray
            .map { it.jsonObject }

    @Test
    fun `v1 file is migrated to v2 and keeps every project`() =
        runTest {
            writeFile(
                """{"schemaVersion": 1, "data": {"projects": [
                    {"id": "p1", "dir": "/Users/test/alpha", "displayName": "Alpha"},
                    {"id": "p2", "dir": "/Users/test/beta", "displayName": "Beta"}
                ]}}""",
            )

            val api = open()

            api.storageIssue.value shouldBe null
            api.projects.value.map { it.id.value to it.displayName } shouldContainExactly
                listOf("p1" to "Alpha", "p2" to "Beta")
            api.projects.value
                .map { it.subfolders }
                .flatten()
                .shouldBeEmpty()
            stored().getValue("schemaVersion").jsonPrimitive.int shouldBe 2
            storedEntries().map { it.keys } shouldContainExactly
                listOf(setOf("id", "dir", "displayName"), setOf("id", "dir", "displayName"))
            broken.shouldNotExist()
        }

    @Test
    fun `v1 with an unknown subfolders key keeps it`() =
        runTest {
            writeFile(
                """{"schemaVersion": 1, "data": {"projects": [
                    {"id": "p1", "dir": "/Users/test/plant", "displayName": "Plant", "subfolders": ["soft", "docs"]}
                ]}}""",
            )

            val api = open()

            api.projects.value
                .single()
                .subfolders shouldBe subfolderNames("docs", "soft")
            broken.shouldNotExist()
        }

    @Test
    fun `project without subfolders is saved without the key`() =
        runTest {
            val api = open()
            val plant = api.added(dir("plant"))
            dir("plant/soft")
            api.setSubfolders(plant.id, subfolderNames("soft")) shouldBe Outcome.Ok(Unit)
            storedEntries().single().keys shouldBe setOf("id", "dir", "displayName", "subfolders")

            api.setSubfolders(plant.id, emptyList()) shouldBe Outcome.Ok(Unit)

            storedEntries().single().keys shouldBe setOf("id", "dir", "displayName")
        }

    @Test
    fun `a new project is saved without the key`() =
        runTest {
            open().added(dir("plain"))

            storedEntries().single().keys shouldBe setOf("id", "dir", "displayName")
            stored().getValue("schemaVersion").jsonPrimitive.int shouldBe 2
        }

    @Test
    fun `subfolders survive a reopen`() =
        runTest {
            val api = open()
            val plant = api.added(dir("plant"))
            val other = api.added(dir("other"))
            listOf("bob", "Anna", "Carl").forEach { dir("plant/$it") }
            api.setSubfolders(plant.id, subfolderNames("Carl", "bob", "Anna")) shouldBe Outcome.Ok(Unit)

            val reopened = open()

            reopened.projects.value shouldContainExactly
                listOf(plant.copy(subfolders = subfolderNames("Anna", "bob", "Carl")), other)
            reopened.storageIssue.value shouldBe null
        }

    @Test
    fun `v3 file gives NEWER_SCHEMA and is not overwritten`() =
        runTest {
            val original = writeFile("""{"schemaVersion": 3, "data": {"projects": [], "pinned": []}}""")

            val api = open()

            api.storageIssue.value shouldBe StorageIssue.NEWER_SCHEMA
            api.projects.value.shouldBeEmpty()
            api.add(dir("blocked")) shouldBe Outcome.Err(ProjectError.StorageFailed(StorageProblem.NEWER_SCHEMA))
            api.setSubfolders(
                dev.quayside.core.domain
                    .ProjectId("p1"),
                emptyList(),
            ) shouldBe
                Outcome.Err(ProjectError.StorageFailed(StorageProblem.NEWER_SCHEMA))
            file.readBytes() shouldBe original
            broken.shouldNotExist()
        }

    @Test
    fun `v2 file with a repeated subfolder is moved to broken`() =
        runTest {
            val original =
                writeFile(
                    """{"schemaVersion": 2, "data": {"projects": [
                        {"id": "p1", "dir": "/Users/test/plant", "displayName": "Plant", "subfolders": ["soft", "soft"]}
                    ]}}""",
                )

            val api = open()

            api.storageIssue.value shouldBe StorageIssue.RECOVERED_FROM_BROKEN_FILE
            api.projects.value.shouldBeEmpty()
            broken.readBytes() shouldBe original
            file.shouldNotExist()
        }

    @Test
    fun `v1 file with subfolders that is not an array is moved to broken`() =
        runTest {
            val original =
                writeFile(
                    """{"schemaVersion": 1, "data": {"projects": [
                        {"id": "p1", "dir": "/Users/test/plant", "displayName": "Plant", "subfolders": "soft"}
                    ]}}""",
                )

            val api = open()

            api.storageIssue.value shouldBe StorageIssue.RECOVERED_FROM_BROKEN_FILE
            api.projects.value.shouldBeEmpty()
            broken.readBytes() shouldBe original
        }

    @Test
    fun `stored subfolder of a since deleted directory is still listed`() =
        runTest {
            val api = open()
            val plant = api.added(dir("plant"))
            val soft = dir("plant/soft")
            api.setSubfolders(plant.id, subfolderNames("soft"))
            Files.delete(soft)

            val reopened = open()

            reopened.projects.value
                .single()
                .subfolders shouldBe subfolderNames("soft")
            reopened.storageIssue.value shouldBe null
        }

    @Test
    fun `case-insensitive APFS duplicate is stored once`() =
        runTest {
            assumeTrue(isCaseInsensitive(), "the file system tells Soft and soft apart")
            val api = open()
            val plant = api.added(dir("plant"))
            dir("plant/soft")

            api.setSubfolders(plant.id, listOf(SubfolderName("Soft"), SubfolderName("soft"))) shouldBe Outcome.Ok(Unit)

            api.projects.value
                .single()
                .subfolders shouldBe subfolderNames("soft")
            open()
                .projects.value
                .single()
                .subfolders shouldBe subfolderNames("soft")
        }

    @Test
    fun `write failure on setSubfolders returns WRITE_FAILED and the old file stays`() =
        runTest {
            assumeTrue("posix" in FileSystems.getDefault().supportedFileAttributeViews())
            val api = open()
            val plant = api.added(dir("plant"))
            dir("plant/soft")
            val original = file.readBytes()
            Files.setPosixFilePermissions(storageDir, PosixFilePermissions.fromString("r-x------"))
            try {
                assumeFalse(Files.isWritable(storageDir), "running as root: permissions are not enforced")

                api.setSubfolders(plant.id, subfolderNames("soft")) shouldBe
                    Outcome.Err(ProjectError.StorageFailed(StorageProblem.WRITE_FAILED))

                api.projects.value shouldContainExactly listOf(plant)
            } finally {
                Files.setPosixFilePermissions(storageDir, PosixFilePermissions.fromString("rwx------"))
            }
            file.readBytes() shouldBe original
            file.shouldExist()
        }

    /** Whether `probe` and `PROBE` are one file here (APFS by default; a case-sensitive volume says no). */
    private fun isCaseInsensitive(): Boolean {
        val probe = Files.createFile(tempDir.resolve("CaseProbe"))
        return Files.exists(tempDir.resolve("caseprobe")).also { Files.delete(probe) }
    }

    private companion object {
        const val FILE = "projects.json"
    }
}
