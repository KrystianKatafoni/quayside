package dev.quayside.sessions.impl

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SubfolderProblem
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/** Where a start runs (AM-308 plan, rule 3): membership first, then the disk; the root dir is never checked. */
class StartDirsTest {
    private val projects =
        FakeProjectsApi(
            listOf(
                aProject(PLANT.value, PLANT_DIR, subfolders = subfolderNames("Marketing", "soft")),
                // Listed names that are not direct children: a guard against a corrupt or hand-edited catalogue.
                aProject(ODD.value, ODD_DIR, subfolders = subfolderNames("..", ".", "a/b", "soft/..")),
            ),
        )

    /** One row of the table: [title] names it; [project] is the id text (a value class would be unboxed by JUnit). */
    data class Case(
        val title: String,
        val project: String,
        val subfolder: String?,
        val onDisk: Boolean,
        val expected: Outcome<Path, SessionError>,
        val checked: List<Path>,
    ) {
        override fun toString(): String = title
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `resolve checks membership, then the disk`(case: Case) =
        runTest {
            val asked = mutableListOf<Path>()
            val dirs =
                StartDirs(projects) {
                    asked.add(it)
                    case.onDisk
                }

            dirs.resolve(ProjectId(case.project), case.subfolder) shouldBe case.expected

            asked shouldBe case.checked
        }

    @Test
    fun `has knows the registered projects only`() {
        val dirs = StartDirs(projects) { true }

        listOf(dirs.has(PLANT), dirs.has(ProjectId("unknown"))) shouldBe listOf(true, false)
    }

    companion object {
        private val PLANT = ProjectId("plant-puls")
        private val PLANT_DIR: Path = Path.of("/p/plant-puls")

        private val ODD = ProjectId("odd")
        private val ODD_DIR: Path = Path.of("/p/odd")

        private fun err(
            problem: SubfolderProblem,
            project: ProjectId = PLANT,
        ): Outcome<Path, SessionError> = Outcome.Err(SessionError.SubfolderUnavailable(project, problem))

        private fun oddCase(name: String) =
            Case(
                "a listed name that is not a direct child is rejected: $name",
                ODD.value,
                name,
                true,
                err(SubfolderProblem.NOT_IN_PROJECT, ODD),
                emptyList<Path>(),
            )

        @JvmStatic
        fun cases(): List<Case> = registeredCases() + listOf("..", ".", "a/b", "soft/..").map(::oddCase)

        @Suppress("LongMethod") // a table of cases
        private fun registeredCases(): List<Case> =
            listOf(
                Case(
                    "no subfolder: the project dir, disk not asked",
                    PLANT.value,
                    null,
                    true,
                    Outcome.Ok(PLANT_DIR),
                    emptyList<Path>(),
                ),
                Case(
                    "no subfolder, root missing on disk",
                    PLANT.value,
                    null,
                    false,
                    Outcome.Ok(PLANT_DIR),
                    emptyList<Path>(),
                ),
                Case(
                    "listed and on disk",
                    PLANT.value,
                    "soft",
                    true,
                    Outcome.Ok(PLANT_DIR.resolve("soft")),
                    listOf(PLANT_DIR.resolve("soft")),
                ),
                Case(
                    "letter case is kept: Marketing",
                    PLANT.value,
                    "Marketing",
                    true,
                    Outcome.Ok(PLANT_DIR.resolve("Marketing")),
                    listOf(PLANT_DIR.resolve("Marketing")),
                ),
                Case(
                    "another letter case is not listed",
                    PLANT.value,
                    "marketing",
                    true,
                    err(SubfolderProblem.NOT_IN_PROJECT),
                    emptyList<Path>(),
                ),
                Case(
                    "a parent path is not listed",
                    PLANT.value,
                    "../other",
                    true,
                    err(SubfolderProblem.NOT_IN_PROJECT),
                    emptyList<Path>(),
                ),
                Case(
                    "a nested path is not listed",
                    PLANT.value,
                    "soft/x",
                    true,
                    err(SubfolderProblem.NOT_IN_PROJECT),
                    emptyList<Path>(),
                ),
                Case(
                    "a directory the group does not list",
                    PLANT.value,
                    "docs",
                    true,
                    err(SubfolderProblem.NOT_IN_PROJECT),
                    emptyList<Path>(),
                ),
                Case(
                    "listed but not on disk",
                    PLANT.value,
                    "soft",
                    false,
                    err(SubfolderProblem.MISSING),
                    listOf(PLANT_DIR.resolve("soft")),
                ),
                Case(
                    "unknown project comes first",
                    "nope",
                    "soft",
                    true,
                    Outcome.Err(SessionError.UnknownProject(ProjectId("nope"))),
                    emptyList<Path>(),
                ),
            )
    }
}
