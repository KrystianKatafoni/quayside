package dev.quayside.agentruntime.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.getOrNull
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Behaviour every [AgentRuntime] must have for session operations (ENG-T2). Runs on
 * [FakeAgentRuntime] and, from AM-203, on the CLI adapter with a fake `claude` executable.
 * Health is covered separately by [AgentRuntimeHealthContract].
 */
public abstract class AgentRuntimeContract {
    /** Returns a runtime whose supervisor starts in the state described by [world]. */
    public abstract fun create(world: RuntimeWorld): AgentRuntime

    /**
     * Existing directory used as `cwd` by every `start` in this contract; the CLI adapter runs
     * `claude --bg` in it. Read only inside test methods, so an override may use injected temp dirs.
     */
    protected open val startCwd: Path = A_PROJECT_DIR

    private val first = aRuntimeSession(id = SessionId("7c5dcf5d"), name = "fix-login")
    private val second =
        aRuntimeSession(
            id = SessionId("a1b2c3d4"),
            conversationId = aConversationId("9d1f2e3c-0000-4000-8000-000000000002"),
            name = "write-docs",
            cwd = Path.of("/Users/test/projects/other"),
            phase = RuntimePhase.BLOCKED,
            waitingFor = WaitingFor.Permission,
            pid = 4343L,
        )
    private val unknown = SessionId("deadbeef")

    private suspend fun AgentRuntime.listed(): List<RuntimeSession> = listSessions().getOrNull().shouldNotBeNull()

    private suspend fun AgentRuntime.find(id: SessionId): RuntimeSession? = listed().find { it.id == id }

    @Test
    public fun `listSessions returns every session of the world`(): Unit =
        runTest {
            val runtime = create(RuntimeWorld(sessions = listOf(first, second)))

            runtime.listed() shouldContainExactlyInAnyOrder listOf(first, second)
        }

    @Test
    public fun `listSessions on an empty world returns an empty list`(): Unit =
        runTest {
            create(RuntimeWorld()).listSessions() shouldBe Outcome.Ok(emptyList())
        }

    @Test
    public fun `listSessions under a directory returns sessions in it and below it only`(): Unit =
        runTest {
            val inDir = aRuntimeSession(id = SessionId("00000001"), cwd = Path.of("/p/a"))
            val below = aRuntimeSession(id = SessionId("00000002"), cwd = Path.of("/p/a/sub"))
            val sibling = aRuntimeSession(id = SessionId("00000003"), cwd = Path.of("/p/ab"))
            val elsewhere = aRuntimeSession(id = SessionId("00000004"), cwd = Path.of("/q"))
            val runtime = create(RuntimeWorld(sessions = listOf(inDir, below, sibling, elsewhere)))

            val listed = runtime.listSessions(under = Path.of("/p/a")).getOrNull().shouldNotBeNull()

            listed.map { it.id } shouldContainExactlyInAnyOrder listOf(inDir.id, below.id)
        }

    @Test
    public fun `started session appears in the list as working with its name and cwd`(): Unit =
        runTest {
            val runtime = create(RuntimeWorld())
            val cwd = startCwd

            val id = runtime.start(aStartSpec(cwd = cwd, name = "new-feature")).getOrNull().shouldNotBeNull()

            val started = runtime.find(id).shouldNotBeNull()
            assertSoftly(started) {
                name shouldBe "new-feature"
                this.cwd shouldBe cwd
                phase shouldBe RuntimePhase.WORKING
            }
        }

    @Test
    public fun `start returns a new id each time`(): Unit =
        runTest {
            val runtime = create(RuntimeWorld(sessions = listOf(first)))

            val a = runtime.start(aStartSpec(cwd = startCwd, name = "one")).getOrNull().shouldNotBeNull()
            val b = runtime.start(aStartSpec(cwd = startCwd, name = "two")).getOrNull().shouldNotBeNull()

            a shouldNotBe b
            a shouldNotBe first.id
            b shouldNotBe first.id
        }

    @Test
    public fun `stop makes the session stopped`(): Unit =
        runTest {
            val runtime = create(RuntimeWorld(sessions = listOf(first)))

            runtime.stop(first.id) shouldBe Outcome.Ok(Unit)

            runtime.find(first.id)?.phase shouldBe RuntimePhase.STOPPED
        }

    @Test
    public fun `respawn brings a stopped session back to working`(): Unit =
        runTest {
            val stopped = first.copy(phase = RuntimePhase.STOPPED, pid = null)
            val runtime = create(RuntimeWorld(sessions = listOf(stopped)))

            runtime.respawn(stopped.id) shouldBe Outcome.Ok(Unit)

            runtime.find(stopped.id)?.phase shouldBe RuntimePhase.WORKING
        }

    @Test
    public fun `remove makes the session disappear from the list`(): Unit =
        runTest {
            val runtime = create(RuntimeWorld(sessions = listOf(first, second)))

            runtime.remove(first.id) shouldBe Outcome.Ok(Unit)

            runtime.listed().map { it.id } shouldBe listOf(second.id)
        }

    @Test
    public fun `operations on an unknown session return UnknownSession`(): Unit =
        runTest {
            val runtime = create(RuntimeWorld(sessions = listOf(first)))
            val expected = Outcome.Err(RuntimeError.UnknownSession(unknown))

            assertSoftly {
                runtime.stop(unknown) shouldBe expected
                runtime.respawn(unknown) shouldBe expected
                runtime.remove(unknown) shouldBe expected
                runtime.recentOutput(unknown) shouldBe expected
            }
            runtime.listed() shouldBe listOf(first)
        }

    @Test
    public fun `recentOutput returns the output of the session`(): Unit =
        runTest {
            val runtime =
                create(
                    RuntimeWorld(
                        sessions = listOf(first, second),
                        outputs = mapOf(first.id to "Running tests...\nAll green", second.id to "other"),
                    ),
                )

            runtime.recentOutput(first.id) shouldBe Outcome.Ok("Running tests...\nAll green")
        }

    @Test
    public fun `attachCommand refers to the session id`() {
        val runtime = create(RuntimeWorld(sessions = listOf(first)))

        runtime.attachCommand(first.id).argv shouldContain first.id.value
    }

    @Test
    public fun `every operation returns NotInstalled when claude is missing`(): Unit =
        runTest {
            val runtime = create(RuntimeWorld(sessions = listOf(first), installed = false))
            val expected = Outcome.Err(RuntimeError.NotInstalled)

            assertSoftly {
                runtime.listSessions() shouldBe expected
                runtime.start(aStartSpec(cwd = startCwd)) shouldBe expected
                runtime.stop(first.id) shouldBe expected
                runtime.respawn(first.id) shouldBe expected
                runtime.remove(first.id) shouldBe expected
                runtime.recentOutput(first.id) shouldBe expected
            }
        }
}
