@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.FakeAgentEventSource
import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.getOrNull
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SubfolderProblem
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The disk check of a start in a subfolder as `sessionsModule` wires it (AM-308 plan, rule 3): with an `io`
 * dispatcher it asks the real disk without following symlinks; without one it accepts every subfolder.
 */
class FileStartDirsTest {
    @TempDir
    lateinit var root: Path

    private val project = ProjectId("group")

    private fun TestScope.commands(withIo: Boolean): SessionCommands {
        val projects =
            FakeProjectsApi(
                listOf(Project(project, root.toRealPath(), "group", subfolderNames("file", "gone", "link", "ok"))),
            )
        return sessionsModule(
            runtime = FakeAgentRuntime(),
            hooks = FakeAgentEventSource(),
            projects = projects,
            scope = backgroundScope,
            clock = virtualClock(),
            logger = NoOpLogger,
            io = if (withIo) StandardTestDispatcher(testScheduler) else null,
        ).commands
    }

    @Test
    fun `existing subfolder directory is accepted`() =
        runTest {
            Files.createDirectory(root.resolve("ok"))
            val commands = commands(withIo = true)

            commands.startIn("ok").getOrNull().shouldNotBeNull()
        }

    @Test
    fun `missing directory, a file and a symlink are MISSING`() =
        runTest {
            Files.createFile(root.resolve("file"))
            Files.createDirectory(root.resolve("target"))
            Files.createSymbolicLink(root.resolve("link"), root.resolve("target"))
            val commands = commands(withIo = true)

            listOf("gone", "file", "link").map { commands.startIn(it) } shouldBe
                List(3) { Outcome.Err(SessionError.SubfolderUnavailable(project, SubfolderProblem.MISSING)) }
        }

    @Test
    fun `without an io dispatcher every listed subfolder is accepted`() =
        runTest {
            val commands = commands(withIo = false)

            commands.startIn("gone").getOrNull().shouldNotBeNull()
        }

    private suspend fun SessionCommands.startIn(name: String) =
        start(project, NewSessionSpec(prompt = "go", subfolder = name))
}
