package dev.quayside.projects.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.paths.shouldBeADirectory
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files

/**
 * Behaviour every [ProjectsApi] must have (ENG-T2). Runs on [FakeProjectsApi] reading the disk and,
 * from AM-302, on `JsonProjectStore`. Extend this class: it also runs the path cases of
 * [ProjectsApiPathContract] (normalization, symlinks, missing paths), with the same temp directory
 * rules.
 */
public abstract class ProjectsApiContract : ProjectsApiPathContract() {
    @Test
    public fun `list is empty at start`(): Unit =
        runTest {
            api().projects.value.shouldBeEmpty()
        }

    @Test
    public fun `fresh store reports no storage issue`(): Unit =
        runTest {
            api().storageIssue.value shouldBe null
        }

    @Test
    public fun `added project appears in the list`(): Unit =
        runTest {
            val api = api()
            val dir = directory("agent-manager")

            val project = api.added(dir, "Quayside")

            project.dir shouldBe dir
            project.displayName shouldBe "Quayside"
            api.projects.value shouldContainExactly listOf(project)
        }

    @Test
    public fun `add without a name uses the directory name`(): Unit =
        runTest {
            val dir = directory("agent-manager")

            api().added(dir).displayName shouldBe "agent-manager"
        }

    @Test
    public fun `add without a name of a long directory name succeeds`(): Unit =
        runTest {
            val name = api().added(directory("x".repeat(LONG_DIRECTORY_NAME))).displayName

            name.codePointCount(0, name.length) shouldBe ProjectInputRules.MAX_DISPLAY_NAME_LENGTH
            name shouldBe "x".repeat(ProjectInputRules.MAX_DISPLAY_NAME_LENGTH - 1) + "\u2026"
        }

    @Test
    public fun `add with a too long name returns TOO_LONG and adds nothing`(): Unit =
        runTest {
            val api = api()

            api.add(directory("agent-manager"), TOO_LONG_NAME) shouldBe
                Outcome.Err(ProjectError.InvalidDisplayName(NameProblem.TOO_LONG))

            api.projects.value.shouldBeEmpty()
        }

    @Test
    public fun `add with a name trims it`(): Unit =
        runTest {
            val dir = directory("plant")

            api().added(dir, "  Plant  ").displayName shouldBe "Plant"
        }

    @Test
    public fun `projects keep the order in which they were added`(): Unit =
        runTest {
            val api = api()
            val dirs = listOf(directory("zeta"), directory("alpha"), directory("mid"))

            val added = dirs.map { api.added(it) }

            api.projects.value.map { it.dir } shouldContainExactly dirs
            added.map { it.id }.toSet() shouldHaveSize dirs.size
        }

    @Test
    public fun `adding the same directory twice returns AlreadyAdded`(): Unit =
        runTest {
            val api = api()
            val dir = directory("agent-manager")
            val first = api.added(dir)

            api.add(dir, "Another name") shouldBe Outcome.Err(ProjectError.AlreadyAdded(first.id))

            api.projects.value shouldContainExactly listOf(first)
        }

    @Test
    public fun `concurrent adds of the same directory give exactly one project`(): Unit =
        runTest {
            val api = api()
            val dir = directory("agent-manager")

            val start = CompletableDeferred<Unit>()

            val results =
                withContext(Dispatchers.Default) {
                    val adds =
                        List(CONCURRENT_ADDS) {
                            async {
                                start.await()
                                api.add(dir)
                            }
                        }
                    start.complete(Unit)
                    adds.awaitAll()
                }

            val project = api.projects.value.single()
            results.count { it is Outcome.Ok } shouldBe 1
            results.filterIsInstance<Outcome.Err<ProjectError>>().map { it.error }.toSet() shouldBe
                setOf(ProjectError.AlreadyAdded(project.id))
        }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["", "   ", "a\nb"])
    public fun `invalid name returns InvalidDisplayName and adds nothing`(name: String): Unit =
        runTest {
            val api = api()
            val dir = directory("agent-manager")

            api.add(dir, name).shouldBeInstanceOf<Outcome.Err<ProjectError.InvalidDisplayName>>()

            api.projects.value.shouldBeEmpty()
        }

    @Test
    public fun `remove makes the project disappear`(): Unit =
        runTest {
            val api = api()
            val kept = api.added(directory("kept"))
            val removed = api.added(directory("removed"))

            api.remove(removed.id) shouldBe Outcome.Ok(Unit)

            api.projects.value shouldContainExactly listOf(kept)
        }

    @Test
    public fun `remove never deletes files on disk`(): Unit =
        runTest {
            val api = api()
            val dir = directory("agent-manager")
            val readme = file("agent-manager/README.md")
            val project = api.added(dir)

            api.remove(project.id) shouldBe Outcome.Ok(Unit)

            dir.shouldBeADirectory()
            readme.shouldExist()
            Files.readString(readme) shouldBe "content"
        }

    @Test
    public fun `remove of an unknown project returns UnknownProject`(): Unit =
        runTest {
            val api = api()
            val project = api.added(directory("agent-manager"))
            val unknown = ProjectId("no-such-project")

            api.remove(unknown) shouldBe Outcome.Err(ProjectError.UnknownProject(unknown))

            api.projects.value shouldContainExactly listOf(project)
        }

    @Test
    public fun `rename changes only the display name and keeps the position`(): Unit =
        runTest {
            val api = api()
            val first = api.added(directory("first"))
            val second = api.added(directory("second"))
            val third = api.added(directory("third"))

            api.rename(second.id, "  Renamed  ") shouldBe Outcome.Ok(Unit)

            api.projects.value shouldContainExactly listOf(first, second.copy(displayName = "Renamed"), third)
        }

    @Test
    public fun `rename with a blank name returns InvalidDisplayName`(): Unit =
        runTest {
            val api = api()
            val project = api.added(directory("agent-manager"))

            api.rename(project.id, "   ") shouldBe
                Outcome.Err(ProjectError.InvalidDisplayName(NameProblem.BLANK))

            api.projects.value shouldContainExactly listOf(project)
        }

    @Test
    public fun `rename with a too long name returns TOO_LONG`(): Unit =
        runTest {
            val api = api()
            val project = api.added(directory("agent-manager"))

            api.rename(project.id, TOO_LONG_NAME) shouldBe
                Outcome.Err(ProjectError.InvalidDisplayName(NameProblem.TOO_LONG))

            api.projects.value shouldContainExactly listOf(project)
        }

    @Test
    public fun `rename of an unknown project returns UnknownProject`(): Unit =
        runTest {
            val api = api()
            val unknown = ProjectId("no-such-project")

            api.rename(unknown, "Name") shouldBe Outcome.Err(ProjectError.UnknownProject(unknown))

            api.projects.value.shouldBeEmpty()
        }

    @Test
    public fun `removed directory can be added again`(): Unit =
        runTest {
            val api = api()
            val dir = directory("agent-manager")
            val first = api.added(dir)
            api.remove(first.id)

            val again = api.added(dir)

            again.dir shouldBe dir
            api.projects.value shouldContainExactly listOf(again)
        }

    private companion object {
        const val CONCURRENT_ADDS = 32
        const val LONG_DIRECTORY_NAME = 120
        val TOO_LONG_NAME = "x".repeat(ProjectInputRules.MAX_DISPLAY_NAME_LENGTH + 1)
    }
}
