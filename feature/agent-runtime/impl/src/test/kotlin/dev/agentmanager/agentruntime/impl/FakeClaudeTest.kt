package dev.agentmanager.agentruntime.impl

import dev.agentmanager.agentruntime.api.RuntimePhase
import dev.agentmanager.agentruntime.api.RuntimeWorld
import dev.agentmanager.agentruntime.api.WaitingFor
import dev.agentmanager.agentruntime.api.aRuntimeSession
import dev.agentmanager.core.domain.Outcome
import dev.agentmanager.core.domain.SessionId
import dev.agentmanager.core.domain.ShellCommand
import dev.agentmanager.core.domain.getOrNull
import dev.agentmanager.core.process.LoginShellProcessRunner
import dev.agentmanager.core.process.ProcessError
import dev.agentmanager.core.process.ProcessResult
import dev.agentmanager.core.testing.T0
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.fail
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories

/**
 * Checks of the fake `claude` itself, without the adapter: the contract and integration tests are
 * only as good as the fake, and these stay green while the adapter is still missing.
 */
class FakeClaudeTest {
    @TempDir
    lateinit var dir: Path

    private val io = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val runner = LoginShellProcessRunner(io, TEST_SHELL)

    @AfterEach
    fun closeDispatcher() = io.close()

    private val working = aRuntimeSession(id = SessionId("7c5dcf5d"), name = "fix \"login\"")
    private val permission =
        aRuntimeSession(
            id = SessionId("a1b2c3d4"),
            conversationId = null,
            phase = RuntimePhase.BLOCKED,
            waitingFor = WaitingFor.Permission,
            pid = 4343L,
        )
    private val input =
        aRuntimeSession(id = SessionId("b2c3d4e5"), phase = RuntimePhase.BLOCKED, waitingFor = WaitingFor.Input)
    private val stopped = aRuntimeSession(id = SessionId("c3d4e5f6"), phase = RuntimePhase.STOPPED, pid = null)
    private val done = aRuntimeSession(id = SessionId("d4e5f6a7"), phase = RuntimePhase.DONE)

    private suspend fun FakeClaude.run(
        vararg args: String,
        workDir: Path = dir,
    ): ProcessResult =
        when (val outcome = runner.run(ShellCommand(listOf(executable) + args, workDir))) {
            is Outcome.Ok -> outcome.value
            is Outcome.Err -> fail("fake claude did not run: ${outcome.error}")
        }

    private suspend fun FakeClaude.listed(vararg flags: String) =
        when (val parsed = AgentsJsonParser.parse(run("agents", "--json", *flags).stdout, T0)) {
            is Outcome.Ok -> parsed.value.sessions
            is Outcome.Err -> fail("fake claude printed unparseable agents json: ${parsed.error}")
        }

    @Test
    fun `agents json all prints the world as the parser reads it`() =
        runTest {
            val world = listOf(working, permission, input, stopped, done)
            val claude = FakeClaude.install(dir.resolve("c"), RuntimeWorld(sessions = world))

            claude.listed("--all") shouldContainExactly world
        }

    @Test
    fun `agents json without all hides finished sessions and cwd is rejected`() =
        runTest {
            val claude = FakeClaude.install(dir.resolve("c"), RuntimeWorld(sessions = listOf(working, stopped, done)))

            claude.listed() shouldContainExactly listOf(working)
            claude.run("agents", "--json", "--cwd", "/p").exitCode shouldBe 1
        }

    @Test
    fun `bg prints the id after a banner and records a working session in the cwd`() =
        runTest {
            val claude = FakeClaude.install(dir.resolve("c"), RuntimeWorld(sessions = listOf(working)))
            val project = dir.resolve("project").createDirectories().toRealPath()

            val result = claude.run("--bg", "--name", "new-one", "--", "Do it", workDir = project)

            result.exitCode shouldBe 0
            result.stdout.lines().first() shouldBe "backgrounded · 00000001 · new-one"
            result.stderr shouldBe "Starting background service…\n"
            val started = claude.listed().single { it.id == SessionId("00000001") }
            started.name shouldBe "new-one"
            started.cwd shouldBe project
            started.phase shouldBe RuntimePhase.WORKING
            started.startedAt shouldBe T0
        }

    @Test
    fun `stop, respawn, rm and logs change and read the state`() =
        runTest {
            val claude =
                FakeClaude.install(
                    dir.resolve("c"),
                    RuntimeWorld(sessions = listOf(working, permission), outputs = mapOf(working.id to "a\nb")),
                )

            claude.run("stop", "7c5dcf5d") shouldBe ProcessResult(0, "stopped 7c5dcf5d\n", "")
            claude.listed("--all").first().phase shouldBe RuntimePhase.STOPPED
            claude.run("respawn", "7c5dcf5d") shouldBe ProcessResult(0, "respawned 7c5dcf5d\n", "")
            claude.listed("--all").first().phase shouldBe RuntimePhase.WORKING
            claude.run("logs", "7c5dcf5d").stdout shouldBe "a\nb"
            claude.run("logs", "a1b2c3d4").stdout shouldBe ""
            claude.run("rm", "7c5dcf5d") shouldBe ProcessResult(0, "removed 7c5dcf5d\n", "")
            claude.listed("--all").map { it.id } shouldBe listOf(permission.id)
        }

    @Test
    fun `unknown id fails with exit 1 and a message on stderr`() =
        runTest {
            val claude = FakeClaude.install(dir.resolve("c"), RuntimeWorld(sessions = listOf(working)))

            val hint = ". Run 'claude agents' to list running sessions."
            val expected = mapOf("stop" to hint, "logs" to hint, "attach" to hint, "respawn" to "", "rm" to "")
            assertSoftly {
                expected.forEach { (verb, tail) ->
                    claude.run(verb, "deadbeef") shouldBe ProcessResult(1, "", "No job matching 'deadbeef'$tail\n")
                }
            }
            claude.sessionLines().size shouldBe 1
        }

    @Test
    fun `stop keeps a finished session done and drops its pid`() =
        runTest {
            val claude = FakeClaude.install(dir.resolve("c"), RuntimeWorld(sessions = listOf(done, permission)))

            claude.run("stop", "d4e5f6a7").exitCode shouldBe 0
            claude.run("stop", "a1b2c3d4").exitCode shouldBe 0

            claude.listed("--all") shouldContainExactly
                listOf(
                    done.copy(pid = null),
                    permission.copy(phase = RuntimePhase.STOPPED, waitingFor = null, pid = null),
                )
        }

    @Test
    fun `respawn of a done session keeps it done with a new pid and start time`() =
        runTest {
            val later = T0.plusSeconds(60)
            val stoppedDone = done.copy(pid = null)
            val claude = FakeClaude.install(dir.resolve("c"), RuntimeWorld(sessions = listOf(stoppedDone)), now = later)

            claude.run("respawn", "d4e5f6a7").exitCode shouldBe 0

            val respawned = claude.listed("--all").single()
            respawned.phase shouldBe RuntimePhase.DONE
            respawned.pid.shouldNotBeNull()
            respawned.startedAt shouldBe later
        }

    @Test
    fun `agents json prints status next to state like 2_1_302`() =
        runTest {
            val claude =
                FakeClaude.install(
                    dir.resolve("c"),
                    RuntimeWorld(
                        sessions =
                            listOf(
                                working,
                                permission,
                                done,
                                stopped,
                                done.copy(id = SessionId("e5f6a7b8"), pid = null),
                            ),
                    ),
                )

            val statuses =
                Json.parseToJsonElement(claude.run("agents", "--json", "--all").stdout).jsonArray.map {
                    it.jsonObject["status"]?.jsonPrimitive?.content
                }

            statuses shouldContainExactly listOf("busy", "waiting", "idle", null, null)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordings")
    fun `answers match the 2_1_302 recordings`(
        recording: String,
        args: List<String>,
    ) = runTest {
        val first = aRuntimeSession(id = SessionId("00000001"), name = "agent-manager-1")
        val claude =
            FakeClaude.install(
                dir.resolve("c"),
                RuntimeWorld(
                    sessions =
                        if (args.first() ==
                            "--bg"
                        ) {
                            emptyList()
                        } else {
                            listOf(first)
                        },
                ),
            )
        claude.setMode(if (recording.endsWith("untrusted-workspace")) "untrusted" else "normal")

        val expected = recorded(recording)

        claude.run(*args.toTypedArray()) shouldBe
            expected.copy(stderr = expected.stderr.replace(RECORDED_UNTRUSTED_CWD, dir.toRealPath().toString()))
    }

    private fun recorded(prefix: String): ProcessResult {
        fun part(ext: String): String =
            requireNotNull(
                javaClass.getResource("/fixtures/cli/recorded/$prefix.$ext"),
            ) { "missing $prefix.$ext" }.readText()

        return ProcessResult(part("exit").trim().toInt(), part("stdout"), part("stderr"))
    }

    @Test
    fun `calls log keeps every argument unchanged`() =
        runTest {
            val claude = FakeClaude.install(dir.resolve("c"), RuntimeWorld())
            val prompt = "\"q\" '\$HOME' `id`\nnext\tline"

            claude.run("--bg", "--", prompt)
            claude.run("agents", "--json", "--all")

            claude.calls() shouldBe listOf(listOf("--bg", "--", prompt), listOf("agents", "--json", "--all"))
        }

    @Test
    fun `fail and garbage modes answer every call`() =
        runTest {
            val claude = FakeClaude.install(dir.resolve("c"), RuntimeWorld(sessions = listOf(working)))

            claude.setMode("fail 4 Error: one\ntwo")
            claude.run("agents", "--json") shouldBe ProcessResult(4, "", "Error: one\ntwo")
            claude.setMode("garbage")
            claude.run("agents", "--json").stdout shouldBe "this is not json {\n"
        }

    @Test
    fun `not installed world is a program the shell does not find`() =
        runTest {
            val claude = FakeClaude.install(dir.resolve("c"), RuntimeWorld(installed = false))

            val outcome = runner.run(ShellCommand(listOf(claude.executable, "agents", "--json"), dir))

            outcome shouldBe Outcome.Err(ProcessError.NotFound(claude.executable))
            claude.calls() shouldBe emptyList()
        }

    @Test
    fun `version prints the world version`() =
        runTest {
            val claude = FakeClaude.install(dir.resolve("c"), RuntimeWorld(version = "2.1.302"))

            claude.run("--version").stdout shouldBe "2.1.302 (Claude Code)\n"
            runner.run(ShellCommand(listOf(claude.executable, "--version"), dir)).getOrNull().shouldNotBeNull()
            claude.setVersion("2.1.100")
            claude.run("--version").stdout shouldBe "2.1.100 (Claude Code)\n"
        }

    @Test
    fun `daemon status reports each state`() =
        runTest {
            val claude = FakeClaude.install(dir.resolve("c"), RuntimeWorld(version = "2.1.302"))

            val running = claude.run("daemon", "status")
            claude.setDaemon(FakeClaude.Daemon.STOPPED)
            val stopped = claude.run("daemon", "status")
            claude.setDaemon(FakeClaude.Daemon.ABSENT)
            val absent = claude.run("daemon", "status")

            assertSoftly {
                running.exitCode shouldBe 0
                running.stdout.lines().map { it.trim() } shouldContainAll
                    listOf("pid:     698", "version: 2.1.302", "bg sessions:", "control.sock: reachable")
                running.stdout.lines().map { it.substringBefore(':').trim() } shouldContainAll
                    listOf("pid", "version", "uptime", "origin")
                stopped shouldBe ProcessResult(1, "not running\n", "")
                absent shouldBe ProcessResult(2, "", "fake-claude: unknown command daemon\n")
            }
            claude.calls() shouldBe List(3) { listOf("daemon", "status") }
        }

    @Test
    fun `agents fails when Agent View is disabled`() =
        runTest {
            val byEnv = FakeClaude.install(dir.resolve("env"), RuntimeWorld(agentViewEnabled = false))
            val bySetting = FakeClaude.install(dir.resolve("setting"), RuntimeWorld(sessions = listOf(working)))
            bySetting.setAgentView(FakeClaude.AgentView.SETTING)

            assertSoftly {
                byEnv.run("agents", "--json") shouldBe
                    ProcessResult(1, "", "'claude agents --json' is disabled by CLAUDE_CODE_DISABLE_AGENT_VIEW.\n")
                byEnv.run("agents", "--json", "--all").exitCode shouldBe 1
                bySetting.run("agents", "--json") shouldBe
                    ProcessResult(1, "", "'claude agents --json' is disabled by the 'disableAgentView' setting.\n")
            }
            bySetting.setAgentView(FakeClaude.AgentView.ENABLED)
            bySetting.listed() shouldContainExactly listOf(working)
        }

    companion object {
        /** Anonymized cwd in `2.1.302-bg-untrusted-workspace.stderr`; the fake prints its real cwd there. */
        private const val RECORDED_UNTRUSTED_CWD = "/Users/test/projects/untrusted"

        @JvmStatic
        fun recordings(): List<Arguments> =
            listOf(
                arguments("2.1.302-bg", listOf("--bg", "--name", "agent-manager-1", "--", "Run date")),
                arguments(
                    "2.1.302-bg-untrusted-workspace",
                    listOf("--bg", "--name", "agent-manager-1", "--", "Run date"),
                ),
                arguments("2.1.302-stop-done", listOf("stop", "00000001")),
                arguments("2.1.302-respawn-stopped", listOf("respawn", "00000001")),
                arguments("2.1.302-rm-live", listOf("rm", "00000001")),
                arguments("2.1.302-logs-unknown-id", listOf("logs", "deadbeef")),
                arguments("2.1.302-stop-unknown-id", listOf("stop", "deadbeef")),
                arguments("2.1.302-attach-unknown-id", listOf("attach", "deadbeef")),
                arguments("2.1.302-respawn-unknown-id", listOf("respawn", "deadbeef")),
                arguments("2.1.302-rm-unknown-id", listOf("rm", "deadbeef")),
            )
    }
}
