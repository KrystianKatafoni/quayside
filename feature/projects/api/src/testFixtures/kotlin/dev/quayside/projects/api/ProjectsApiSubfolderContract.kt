package dev.quayside.projects.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files
import java.util.Locale

/**
 * Subfolder half of [ProjectsApiContract] (FR-P7, AM-306): [ProjectsApi.setSubfolders] against real directories in
 * the temp directory of [ProjectsApiPathContract]. Implementations extend [ProjectsApiContract], which runs these
 * cases too; symlink cases need a file system with symbolic links (macOS, Linux).
 */
public abstract class ProjectsApiSubfolderContract : ProjectsApiPathContract() {
    /** Adds the project `plant-puls` and creates the directories [children] directly under it. */
    private suspend fun ProjectsApi.group(vararg children: String): Project {
        val project = added(directory("plant-puls"))
        children.forEach { directory("plant-puls/$it") }
        return project
    }

    private fun failure(outcome: Outcome<Unit, ProjectError>): ProjectError =
        outcome.shouldBeInstanceOf<Outcome.Err<ProjectError>>().error

    @Test
    public fun `new project has no subfolders`(): Unit =
        runTest {
            api().group("soft").subfolders shouldBe emptyList()
        }

    @Test
    public fun `set subfolders appear on the project sorted and distinct`(): Unit =
        runTest {
            val api = api()
            val project = api.group("bob", "Anna", "Carl")

            val result = api.setSubfolders(project.id, subfolderNames("Carl", "bob", "Anna", "bob"))

            result shouldBe Outcome.Ok(Unit)
            api.projects.value.single() shouldBe project.copy(subfolders = subfolderNames("Anna", "bob", "Carl"))
        }

    @Test
    public fun `set subfolders keeps id, dir, name and position`(): Unit =
        runTest {
            val api = api()
            val first = api.added(directory("first"))
            val group = api.group("soft")
            val last = api.added(directory("last"), "Last one")

            api.setSubfolders(group.id, subfolderNames("soft")) shouldBe Outcome.Ok(Unit)

            api.projects.value shouldContainExactly listOf(first, group.copy(subfolders = subfolderNames("soft")), last)
        }

    @Test
    public fun `second call replaces the subfolders`(): Unit =
        runTest {
            val api = api()
            val project = api.group("soft", "docs")
            api.setSubfolders(project.id, subfolderNames("soft", "docs"))

            api.setSubfolders(project.id, subfolderNames("docs")) shouldBe Outcome.Ok(Unit)

            api.projects.value
                .single()
                .subfolders shouldBe subfolderNames("docs")
        }

    @Test
    public fun `empty list dissolves the group`(): Unit =
        runTest {
            val api = api()
            val project = api.group("soft")
            api.setSubfolders(project.id, subfolderNames("soft"))

            api.setSubfolders(project.id, emptyList()) shouldBe Outcome.Ok(Unit)

            api.projects.value.single() shouldBe project
        }

    @Test
    public fun `unknown project is rejected`(): Unit =
        runTest {
            val api = api()
            val kept = api.group("soft")
            val unknown = ProjectId("no-such-project")

            api.setSubfolders(unknown, subfolderNames("soft")) shouldBe
                Outcome.Err(ProjectError.UnknownProject(unknown))

            api.projects.value shouldContainExactly listOf(kept)
        }

    @Test
    public fun `missing subfolder is rejected and nothing changes`(): Unit =
        runTest {
            val api = api()
            val project = api.group("soft")

            api.setSubfolders(project.id, subfolderNames("soft", "gone")) shouldBe
                Outcome.Err(ProjectError.InvalidSubfolder(SubfolderName("gone"), SubfolderProblem.MISSING))

            api.projects.value shouldContainExactly listOf(project)
        }

    @Test
    public fun `file instead of a directory is rejected`(): Unit =
        runTest {
            val api = api()
            val project = api.group("soft")
            file("plant-puls/notes.txt")

            api.setSubfolders(project.id, subfolderNames("notes.txt")) shouldBe
                Outcome.Err(ProjectError.InvalidSubfolder(SubfolderName("notes.txt"), SubfolderProblem.NOT_A_DIRECTORY))

            api.projects.value shouldContainExactly listOf(project)
        }

    @Test
    public fun `symlinked subfolder is rejected`(): Unit =
        runTest {
            val api = api()
            val project = api.group("soft")
            symlink("plant-puls/linked", directory("elsewhere"))

            api.setSubfolders(project.id, subfolderNames("linked")) shouldBe
                Outcome.Err(ProjectError.InvalidSubfolder(SubfolderName("linked"), SubfolderProblem.SYMLINK))

            api.projects.value shouldContainExactly listOf(project)
        }

    @Test
    public fun `hidden name is rejected`(): Unit =
        runTest {
            val api = api()
            val project = api.group(".git")

            api.setSubfolders(project.id, subfolderNames(".git")) shouldBe
                Outcome.Err(ProjectError.InvalidSubfolder(SubfolderName(".git"), SubfolderProblem.HIDDEN))

            api.projects.value shouldContainExactly listOf(project)
        }

    @Test
    public fun `name with a slash is rejected as not a name`(): Unit =
        runTest {
            val api = api()
            val project = api.group("soft")
            directory("plant-puls/soft/api")

            api.setSubfolders(project.id, subfolderNames("soft/api")) shouldBe
                Outcome.Err(ProjectError.InvalidSubfolder(SubfolderName("soft/api"), SubfolderProblem.NOT_A_NAME))

            api.projects.value shouldContainExactly listOf(project)
        }

    @Test
    public fun `too many subfolders are rejected`(): Unit =
        runTest {
            val api = api()
            val names = (0..ProjectInputRules.MAX_SUBFOLDERS).map { "dir%03d".format(Locale.ROOT, it) }
            val project = api.group(*names.toTypedArray())

            val error = failure(api.setSubfolders(project.id, names.map(::SubfolderName)))

            error.shouldBeInstanceOf<ProjectError.InvalidSubfolder>().reason shouldBe SubfolderProblem.TOO_MANY
            api.projects.value shouldContainExactly listOf(project)
        }

    @Test
    public fun `exactly the limit of subfolders is accepted`(): Unit =
        runTest {
            val api = api()
            val names = (1..ProjectInputRules.MAX_SUBFOLDERS).map { "dir%03d".format(Locale.ROOT, it) }
            val project = api.group(*names.toTypedArray())

            api.setSubfolders(project.id, names.map(::SubfolderName)) shouldBe Outcome.Ok(Unit)

            api.projects.value
                .single()
                .subfolders.size shouldBe ProjectInputRules.MAX_SUBFOLDERS
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = SubfolderProblem::class, names = ["MISSING", "SYMLINK"])
    public fun `rejected call leaves the earlier subfolders`(problem: SubfolderProblem): Unit =
        runTest {
            val api = api()
            val project = api.group("soft")
            symlink("plant-puls/linked", directory("elsewhere"))
            api.setSubfolders(project.id, subfolderNames("soft"))
            val bad = if (problem == SubfolderProblem.MISSING) "gone" else "linked"

            failure(api.setSubfolders(project.id, subfolderNames("soft", bad)))
                .shouldBeInstanceOf<ProjectError.InvalidSubfolder>()
                .reason shouldBe problem

            api.projects.value
                .single()
                .subfolders shouldBe subfolderNames("soft")
        }

    @Test
    public fun `rename and remove keep subfolders consistent`(): Unit =
        runTest {
            val api = api()
            val project = api.group("soft")
            api.setSubfolders(project.id, subfolderNames("soft"))

            api.rename(project.id, "Plant") shouldBe Outcome.Ok(Unit)
            api.projects.value
                .single()
                .subfolders shouldBe subfolderNames("soft")

            api.remove(project.id) shouldBe Outcome.Ok(Unit)
            Files.isDirectory(root.resolve("plant-puls/soft")) shouldBe true
            api.added(root.resolve("plant-puls")).subfolders shouldBe emptyList()
        }
}
