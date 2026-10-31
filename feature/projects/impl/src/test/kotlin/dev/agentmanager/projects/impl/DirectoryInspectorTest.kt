package dev.agentmanager.projects.impl

import dev.agentmanager.agentruntime.api.AgentRuntime
import dev.agentmanager.agentruntime.api.FakeAgentRuntime
import dev.agentmanager.agentruntime.api.RuntimeError
import dev.agentmanager.agentruntime.api.RuntimePhase
import dev.agentmanager.agentruntime.api.RuntimeWorld
import dev.agentmanager.agentruntime.api.aRuntimeSession
import dev.agentmanager.core.domain.LogLevel
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.getOrNull
import dev.agentmanager.core.process.FakeProcessRunner
import dev.agentmanager.core.process.ProcessError
import dev.agentmanager.core.process.ProcessResult
import dev.agentmanager.core.process.RunOptions
import dev.agentmanager.core.testing.RecordingLogger
import dev.agentmanager.core.testing.aSessionId
import dev.agentmanager.core.testing.testDispatchers
import dev.agentmanager.projects.api.DirectoryReport
import dev.agentmanager.projects.api.FakeProjectsApi
import dev.agentmanager.projects.api.GitInfo
import dev.agentmanager.projects.api.PathKind
import dev.agentmanager.projects.api.ProjectError
import dev.agentmanager.projects.api.ProjectInspector
import dev.agentmanager.projects.api.ProjectsApi
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [projectInspectorModule] with a scripted git ([FakeProcessRunner]) and a fake runtime (AM-303 plan,
 * decisions 1–3 and 7). The directory itself is real, in a temp directory, so path resolution and file
 * reads are the production ones; all coroutines run on the test scheduler.
 */
class DirectoryInspectorTest {
    @TempDir
    lateinit var tempDir: Path

    private val base: Path get() = tempDir.toRealPath()

    private val logger = RecordingLogger()

    private fun projectDir(name: String = "project"): Path = base.resolve(name).createDirectories()

    private fun TestScope.inspector(
        process: FakeProcessRunner = git(),
        runtime: AgentRuntime = FakeAgentRuntime(),
        projects: ProjectsApi = projectsOnDisk(),
        gitTimeout: Duration = 5.seconds,
    ): ProjectInspector =
        projectInspectorModule(
            projects = projects,
            runtime = runtime,
            process = process,
            io = testDispatchers().io,
            logger = logger,
            gitExecutable = GIT,
            gitTimeout = gitTimeout,
        )

    private suspend fun ProjectInspector.report(dir: Path): DirectoryReport = inspect(dir).getOrNull().shouldNotBeNull()

    @Test
    fun `git names the real dir with -C and runs in a neutral dir`() =
        runTest {
            val real = projectDir()
            val link = Files.createSymbolicLink(base.resolve("link"), real)
            val process = git()

            inspector(process).report(link).git shouldBe GitInfo("main")

            process.commands.map { it.argv } shouldContainExactly
                listOf(
                    listOf(GIT, "-C", real.toString()) + SAFE_GIT + listOf("rev-parse", "--is-inside-work-tree"),
                    listOf(
                        GIT,
                        "-C",
                        real.toString(),
                    ) + SAFE_GIT + listOf("symbolic-ref", "--quiet", "--short", "HEAD"),
                )
            process.commands.map { it.workDir } shouldContainExactly listOf(Path.of("/"), Path.of("/"))
        }

    @Test
    fun `each git call uses the git timeout`() =
        runTest {
            val process = git()

            inspector(process, gitTimeout = 7.seconds).report(projectDir())

            process.calls.map { it.second } shouldContainExactly
                listOf(
                    RunOptions(timeout = 7.seconds, maxOutputBytes = 4096),
                    RunOptions(timeout = 7.seconds, maxOutputBytes = 4096),
                )
        }

    @Test
    fun `detached head gives git info without a branch`() =
        runTest {
            val process = git(branch = Outcome.Ok(ProcessResult(1, "", "")))

            inspector(process).report(projectDir()).git shouldBe GitInfo(branch = null)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notWorkTrees")
    fun `outside a work tree the branch is not asked`(
        case: String,
        answer: ProcessResult,
    ) = runTest {
        val process = git(workTree = Outcome.Ok(answer))

        val report = inspector(process).report(projectDir())

        withClue(case) {
            report.git.shouldBeNull()
            process.commands shouldHaveSize 1
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("processErrors")
    fun `every process error gives no git info`(
        case: String,
        error: ProcessError,
    ) = runTest {
        val report = inspector(git(workTree = Outcome.Err(error))).inspect(projectDir())

        withClue(case) {
            report
                .getOrNull()
                .shouldNotBeNull()
                .git
                .shouldBeNull()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("processErrors")
    fun `failed branch call gives no git info`(
        case: String,
        error: ProcessError,
    ) = runTest {
        val report = inspector(git(branch = Outcome.Err(error))).report(projectDir())

        withClue(case) { report.git.shouldBeNull() }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("runtimeErrors")
    fun `runtime error gives no session count`(
        case: String,
        error: RuntimeError,
    ) = runTest {
        val dir = projectDir()
        val runtime = FakeAgentRuntime(RuntimeWorld(sessions = listOf(aRuntimeSession(cwd = dir))))
        runtime.failNext(error)

        withClue(case) { inspector(runtime = runtime).report(dir).sessionCount.shouldBeNull() }
    }

    @Test
    fun `only working and blocked sessions are counted`() =
        runTest {
            val dir = projectDir()
            val sessions =
                RuntimePhase.entries.map {
                    aRuntimeSession(
                        id = aSessionId(it.name.lowercase()),
                        cwd = dir,
                        phase = it,
                    )
                }

            inspector(runtime = FakeAgentRuntime(RuntimeWorld(sessions = sessions))).report(dir).sessionCount shouldBe 2
        }

    @Test
    fun `sessions are listed under the real path`() =
        runTest {
            val real = projectDir()
            val link = Files.createSymbolicLink(base.resolve("link"), real)
            val sessions =
                listOf(
                    aRuntimeSession(id = aSessionId("inside"), cwd = real.resolve("sub")),
                    aRuntimeSession(id = aSessionId("root"), cwd = real),
                    aRuntimeSession(id = aSessionId("sibling"), cwd = projectDir("other")),
                )
            val runtime = FakeAgentRuntime(RuntimeWorld(sessions = sessions))

            inspector(runtime = runtime).report(link).sessionCount shouldBe 2
            runtime.calls shouldBe listOf("listSessions")
        }

    @Test
    fun `alreadyAdded follows the current project list`() =
        runTest {
            val dir = projectDir()
            val projects = projectsOnDisk()
            val inspector = inspector(projects = projects)
            inspector.report(dir).alreadyAdded.shouldBeNull()

            val added = projects.add(dir, displayName = null).getOrNull().shouldNotBeNull()

            inspector.report(dir).alreadyAdded shouldBe added.id
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unresolvable")
    fun `unresolvable path runs neither git nor the runtime`(
        case: String,
        path: (Path) -> Path,
        error: (Path) -> ProjectError,
    ) = runTest {
        Files.writeString(base.resolve("notes.txt"), "content")
        val dir = path(base)
        val process = git()
        val runtime = FakeAgentRuntime()

        val result = inspector(process, runtime).inspect(dir)

        withClue(case) {
            result shouldBe Outcome.Err(error(dir))
            process.commands.shouldBeEmpty()
            runtime.calls.shouldBeEmpty()
        }
    }

    @Test
    fun `logs never contain paths, branch names or git output`() =
        runTest {
            val dir = projectDir(SECRET)
            dir
                .resolve(".claude")
                .createDirectories()
                .resolve("settings.local.json")
                .writeText("{oops $SECRET")
            val failingRuntime =
                FakeAgentRuntime().apply {
                    failNext(
                        RuntimeError.CommandFailed(1, "/Users/$SECRET/x"),
                    )
                }

            inspector(git(branch = Outcome.Ok(ProcessResult(0, "$SECRET\n", "")))).report(dir).git shouldBe
                GitInfo(SECRET)
            inspector(git(workTree = Outcome.Ok(ProcessResult(128, "", DUBIOUS))), failingRuntime).report(dir)
            inspector(git(branch = Outcome.Err(ProcessError.IoFailed("read /Users/$SECRET/.git")))).report(dir)

            logger.entries.shouldNotBeEmpty()
            logger.entries.forEach { entry ->
                withClue(entry) {
                    entry.level shouldBe LogLevel.DEBUG
                    entry.cause.shouldBeNull()
                    "${entry.message} ${entry.fields}" shouldNotContain SECRET
                }
            }
        }

    @Test
    fun `failures are logged at debug with fixed categories`() =
        runTest {
            val dir = projectDir()
            dir
                .resolve(".claude")
                .createDirectories()
                .resolve("settings.local.json")
                .writeText("{oops")
            val runtime = FakeAgentRuntime().apply { failNext(RuntimeError.CommandFailed(1, "boom")) }

            inspector(git(workTree = Outcome.Ok(ProcessResult(128, "", DUBIOUS))), runtime).report(dir)
            inspector(git(workTree = Outcome.Err(ProcessError.Timeout(5.seconds)))).report(dir)

            val logged = logger.entries.map { it.message to it.fields.values.map(Any?::toString) }
            logged.filter { it.first == "inspect git unavailable" }.flatMap { it.second } shouldContain "git:exit"
            logged.filter { it.first == "inspect git unavailable" }.flatMap { it.second } shouldContain
                "process:timeout"
            logged.filter { it.first == "inspect runtime unavailable" }.flatMap { it.second } shouldContain
                "CommandFailed"
            logged.filter { it.first == "inspect settings unreadable" }.flatMap { it.second } shouldContain "local"
        }

    @Test
    fun `projectInspectorModule defaults to git and 5 seconds`() =
        runTest {
            val process = git()
            val inspector =
                projectInspectorModule(projectsOnDisk(), FakeAgentRuntime(), process, testDispatchers().io, logger)

            inspector.inspect(projectDir()).shouldBeInstanceOf<Outcome.Ok<DirectoryReport>>()

            process.calls.shouldNotBeEmpty()
            process.calls.forEach { (command, options) ->
                command.argv.first() shouldBe "git"
                options.timeout shouldBe 5.seconds
            }
        }

    companion object {
        private const val GIT = "/opt/test/bin/git"
        private const val SECRET = "secret-user"
        private val SAFE_GIT =
            listOf("--no-optional-locks", "-c", "core.fsmonitor=false", "-c", "core.hooksPath=/dev/null")
        private const val DUBIOUS =
            "fatal: detected dubious ownership in repository at '/Users/secret-user/project'\n" +
                "To add an exception for this directory, call:\n\n\tgit config --global --add safe.directory /Users/secret-user/project\n"

        private fun ok(stdout: String): Outcome<ProcessResult, ProcessError> = Outcome.Ok(ProcessResult(0, stdout, ""))

        /** Scripted git: answers the work tree check and the branch query, fails on anything else. */
        private fun git(
            workTree: Outcome<ProcessResult, ProcessError> = ok("true\n"),
            branch: Outcome<ProcessResult, ProcessError> = ok("main\n"),
        ): FakeProcessRunner =
            FakeProcessRunner { command ->
                when {
                    "rev-parse" in command.argv -> workTree
                    "symbolic-ref" in command.argv -> branch
                    else -> throw AssertionError("unexpected command ${command.argv}")
                }
            }

        private fun projectsOnDisk(): FakeProjectsApi =
            FakeProjectsApi(pathKind = PathKind::onDisk, realPath = PathKind::realPathOnDisk)

        @JvmStatic
        fun notWorkTrees(): List<Arguments> =
            listOf(
                arguments("rev-parse says false", ProcessResult(0, "false\n", "")),
                arguments("not a repository", ProcessResult(128, "", "fatal: not a git repository\n")),
                arguments("dubious ownership", ProcessResult(128, "", DUBIOUS)),
            )

        @JvmStatic
        fun processErrors(): List<Arguments> =
            listOf(
                arguments("not found", ProcessError.NotFound("git")),
                arguments("timeout", ProcessError.Timeout(5.seconds)),
                arguments("start failed", ProcessError.StartFailed("No such file or directory")),
                arguments("io failed", ProcessError.IoFailed("Broken pipe")),
            )

        @JvmStatic
        fun runtimeErrors(): List<Arguments> =
            listOf(
                arguments("not installed", RuntimeError.NotInstalled),
                arguments("agent view disabled", RuntimeError.AgentViewDisabled),
                arguments("command failed", RuntimeError.CommandFailed(1, "error")),
                arguments("unparseable", RuntimeError.Unparseable("json")),
                arguments("timeout", RuntimeError.Timeout),
            )

        @JvmStatic
        fun unresolvable(): List<Arguments> =
            listOf(
                arguments(
                    "relative path",
                    { _: Path -> Path.of("projects/a") },
                    { p: Path -> ProjectError.NotAbsolute(p) },
                ),
                arguments(
                    "missing directory",
                    { b: Path -> b.resolve("missing") },
                    { p: Path -> ProjectError.NotFound(p) },
                ),
                arguments(
                    "file instead of directory",
                    { b: Path -> b.resolve("notes.txt") },
                    { p: Path -> ProjectError.NotADirectory(p) },
                ),
            )
    }
}
