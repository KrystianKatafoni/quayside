package dev.quayside.projects.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.getOrNull
import dev.quayside.core.storage.InMemoryJsonStore
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.testDispatchers
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectError
import dev.quayside.projects.api.subfolderNames
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** `setSubfolders` checks the disk outside the lock, so the project is looked up again under it (FR-P7, AM-306). */
class JsonProjectStoreSubfoldersTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `project removed while the disk is checked gives UnknownProject and saves nothing`() =
        runTest {
            val store = InMemoryJsonStore(emptyList<Project>())
            val api = JsonProjectStore.open(store, testDispatchers().io, RecordingLogger()) { ProjectId("p1") }
            val dir = Files.createDirectories(tempDir.toRealPath().resolve("plant"))
            Files.createDirectories(dir.resolve("soft"))
            val project = api.add(dir).getOrNull().shouldNotBeNull()

            // The check is parked on the io dispatcher until the scheduler runs
            val pending =
                async(start = CoroutineStart.UNDISPATCHED) { api.setSubfolders(project.id, subfolderNames("soft")) }
            api.remove(project.id) shouldBe Outcome.Ok(Unit)
            val savesAfterRemove = store.saves

            pending.await() shouldBe Outcome.Err(ProjectError.UnknownProject(project.id))
            store.saves shouldBe savesAfterRemove
            api.projects.value.shouldBeEmpty()
        }
}
