package dev.quayside.projects.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** What [FakeProjectsApi] and [FakeProjectInspector] add for subfolders, beyond the contracts (AM-306). */
class FakeSubfoldersTest {
    private val group = aProject(id = "project0001", dir = Path.of("/Users/test/plant-puls"))
    private val storageFailed = ProjectError.StorageFailed(StorageProblem.WRITE_FAILED)

    @Test
    fun `default fake accepts names without touching the disk`() =
        runTest {
            val api = FakeProjectsApi(initial = listOf(group))

            api.setSubfolders(group.id, subfolderNames("soft", "docs")) shouldBe Outcome.Ok(Unit)

            api.projects.value
                .single()
                .subfolders shouldBe subfolderNames("docs", "soft")
        }

    @Test
    fun `setSubfolders is recorded`() =
        runTest {
            val api = FakeProjectsApi(initial = listOf(group))

            api.setSubfolders(group.id, subfolderNames("soft"))

            api.calls shouldBe listOf("setSubfolders project0001")
        }

    @Test
    fun `failNext fails setSubfolders and changes nothing`() =
        runTest {
            val api = FakeProjectsApi(initial = listOf(group))
            api.failNext(storageFailed)

            api.setSubfolders(group.id, subfolderNames("soft")) shouldBe Outcome.Err(storageFailed)

            api.projects.value shouldContainExactly listOf(group)
            api.setSubfolders(group.id, subfolderNames("soft")) shouldBe Outcome.Ok(Unit)
        }

    @Test
    fun `unusable storage fails setSubfolders`() =
        runTest {
            val api = FakeProjectsApi(initial = listOf(group), storageIssue = StorageIssue.NEWER_SCHEMA)

            api.setSubfolders(group.id, subfolderNames("soft")) shouldBe
                Outcome.Err(ProjectError.StorageFailed(StorageProblem.NEWER_SCHEMA))
        }

    @Test
    fun `pathKind and isSymlink decide what is at a subfolder`() =
        runTest {
            val soft = group.dir.resolve("soft")
            val notes = group.dir.resolve("notes")
            val linked = group.dir.resolve("linked")
            val api =
                FakeProjectsApi(
                    initial = listOf(group),
                    pathKind = {
                        if (it ==
                            notes
                        ) {
                            PathKind.FILE
                        } else if (it == soft) {
                            PathKind.DIRECTORY
                        } else {
                            PathKind.MISSING
                        }
                    },
                    isSymlink = { it == linked },
                )

            api.setSubfolders(group.id, subfolderNames("notes")) shouldBe
                Outcome.Err(ProjectError.InvalidSubfolder(SubfolderName("notes"), SubfolderProblem.NOT_A_DIRECTORY))
            api.setSubfolders(group.id, subfolderNames("linked")) shouldBe
                Outcome.Err(ProjectError.InvalidSubfolder(SubfolderName("linked"), SubfolderProblem.SYMLINK))
            api.setSubfolders(group.id, subfolderNames("gone")) shouldBe
                Outcome.Err(ProjectError.InvalidSubfolder(SubfolderName("gone"), SubfolderProblem.MISSING))
            api.setSubfolders(ProjectId("nope"), subfolderNames("soft")) shouldBe
                Outcome.Err(ProjectError.UnknownProject(ProjectId("nope")))
            api.setSubfolders(group.id, subfolderNames("soft")) shouldBe Outcome.Ok(Unit)
        }

    @Test
    fun `inspector lists nothing by default`() =
        runTest {
            FakeProjectInspector().subfolders(group.dir) shouldBe Outcome.Ok(emptyList())
        }

    @Test
    fun `inspector keeps only visible real directories, sorted ignoring case`() =
        runTest {
            val inspector =
                FakeProjectInspector(
                    listing = {
                        listOf(
                            DirEntry("bob"),
                            DirEntry("Anna"),
                            DirEntry(".git"),
                            DirEntry("README.md", isDirectory = false),
                            DirEntry("linked", isSymlink = true),
                            DirEntry("Carl"),
                        )
                    },
                )

            inspector.subfolders(group.dir) shouldBe Outcome.Ok(subfolderNames("Anna", "bob", "Carl"))
        }

    @Test
    fun `inspector answers NotFound, NotADirectory and NotAbsolute like inspect`() =
        runTest {
            val inspector =
                FakeProjectInspector(pathKind = {
                    if (it.fileName.toString() ==
                        "f"
                    ) {
                        PathKind.FILE
                    } else {
                        PathKind.MISSING
                    }
                })
            val file = Path.of("/x/f")
            val missing = Path.of("/x/m")
            val relative = Path.of("x")

            inspector.subfolders(file) shouldBe Outcome.Err(ProjectError.NotADirectory(file))
            inspector.subfolders(missing) shouldBe Outcome.Err(ProjectError.NotFound(missing))
            inspector.subfolders(relative) shouldBe Outcome.Err(ProjectError.NotAbsolute(relative))
        }
}
