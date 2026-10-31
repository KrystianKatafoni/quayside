package dev.agentmanager.projects.impl

import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.storage.BrokenFile
import dev.agentmanager.core.storage.Loaded
import dev.agentmanager.core.storage.StorageError
import dev.agentmanager.projects.api.Project
import dev.agentmanager.projects.api.StorageIssue
import dev.agentmanager.projects.api.StorageProblem
import dev.agentmanager.projects.api.aProject
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/** Start-up mapping and save-error mapping of the AM-302 plan, decision 6. */
class StartupStateTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("loads")
    fun `load result maps to the start-up state`(
        case: String,
        loaded: Outcome<Loaded<List<Project>>, StorageError>,
        projects: List<Project>,
        issue: StorageIssue?,
        blockedBy: StorageProblem?,
    ) {
        withClue(case) { StartupState.of(loaded) shouldBe StartupState(projects, issue, blockedBy) }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("saveErrors")
    fun `every save error is WRITE_FAILED`(
        case: String,
        error: StorageError,
    ) {
        withClue(case) { error.toWriteProblem() shouldBe StorageProblem.WRITE_FAILED }
    }

    companion object {
        private val SECRET = Path.of("/Users/secret-user/Library/Application Support/AgentManager/projects.json")
        private val STORED = listOf(aProject(id = "p1", dir = Path.of("/a")), aProject(id = "p2", dir = Path.of("/b")))

        private val NONE = emptyList<Project>()
        private val BROKEN = BrokenFile(SECRET.resolveSibling("projects.json.broken"), "invalid JSON")

        @JvmStatic
        fun loads(): List<Arguments> =
            listOf(
                arguments("loaded list", Outcome.Ok(Loaded(STORED)), STORED, null, null),
                arguments("loaded empty list", Outcome.Ok(Loaded(NONE)), NONE, null, null),
                arguments(
                    "recovered from a broken file",
                    Outcome.Ok(Loaded(NONE, BROKEN)),
                    NONE,
                    StorageIssue.RECOVERED_FROM_BROKEN_FILE,
                    null,
                ),
                arguments(
                    "newer schema",
                    Outcome.Err(StorageError.NewerSchema(found = 2, supported = 1)),
                    NONE,
                    StorageIssue.NEWER_SCHEMA,
                    StorageProblem.NEWER_SCHEMA,
                ),
                arguments(
                    "read failed",
                    Outcome.Err(StorageError.IoFailed(SECRET, "Permission denied")),
                    NONE,
                    StorageIssue.READ_FAILED,
                    StorageProblem.READ_FAILED,
                ),
            )

        @JvmStatic
        fun saveErrors(): List<Arguments> =
            listOf(
                arguments("io failed", StorageError.IoFailed(SECRET, "No space left on device")),
                arguments("newer schema", StorageError.NewerSchema(found = 2, supported = 1)),
            )
    }
}
