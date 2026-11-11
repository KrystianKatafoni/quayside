@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.AgentEvent
import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.HistoryError
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.aRuntimeSession
import dev.quayside.agentruntime.api.anEventMeta
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.ResumeProblem
import dev.quayside.sessions.api.ResumedSession
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.P1_DIR
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * `RuntimeSessionCommands.resume` (FR-P9, AM-313 plan, "Testy"): every check happens before `claude` is called, and
 * each one has a test in which the runtime sees no `resume` call, so removing the check turns the test red.
 */
class RuntimeSessionResumeTest {
    private val sub: Path = P1_DIR.resolve("src")
    private val disk = SessionId(uuidOf(1))
    private val live = SessionId(uuidOf(1).take(SHORT_ID))

    @Test
    fun `resume calls the runtime once with the uuid and the start directory`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1, cwd = sub)))

            world.commands.resume(disk) shouldBe Outcome.Ok(ResumedSession(live, copy = false))

            world.resumeCalls() shouldBe listOf("resume ${uuidOf(1)}")
            world.session(live.value).cwd shouldBe sub
        }

    @Test
    fun `live session is in the snapshot before resume returns`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1, cwd = sub)))
            world.runtime.lag = 3

            world.commands.resume(disk)

            // the supervisor still lists nothing: the snapshot shows the effect of the command
            val session = world.session(live.value)
            assertSoftly {
                session.state shouldBe SessionState.AwaitingInput(null)
                session.origin shouldBe SessionOrigin.SUPERVISOR
                session.project shouldBe P1
                session.name shouldBe "title 1"
            }
        }

    @Test
    fun `disk row is gone when resume returns`() =
        runTest {
            // another prefix: the supervisor's short id is the first 8 characters of the UUID
            val other = ConversationId("00000002-0000-4000-8000-000000000002")
            val world = resumeWorld(listOf(conversation(1), conversation(2).copy(conversationId = other)))
            world.runtime.lag = 3

            world.commands.resume(disk)

            world.diskIds() shouldBe listOf(other.value)
        }

    @Test
    fun `hook of the resumed conversation applies before the supervisor lists it`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1)))
            world.runtime.lag = 3
            world.commands.resume(disk)

            val meta = anEventMeta(conversationId = conversationOf(1), cwd = P1_DIR, at = T0)
            world.state.onHook(AgentEvent.NeedsInput(meta, "Which file?"))

            // only a session with this conversationId takes the question of the hook
            world.session(live.value).state shouldBe SessionState.AwaitingInput("Which file?")
        }

    @Test
    fun `copy keeps the disk row and shows the copy`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1)))
            world.fake.opensElsewhere(conversationOf(1))

            val result = world.commands.resume(disk)

            result shouldBe Outcome.Ok(ResumedSession(SessionId("fake0001"), copy = true))
            world.diskIds() shouldBe listOf(uuidOf(1))
            world.supervisorIds() shouldBe listOf("fake0001")
        }

    @Test
    fun `file gone since the scan is FILE_MISSING without calling the runtime`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1)))
            world.history.delete(conversationOf(1))
            // the scan has not run again: the row is still listed
            world.diskIds() shouldBe listOf(uuidOf(1))

            val result = world.commands.resume(disk)

            result shouldBe Outcome.Err(SessionError.ResumeUnavailable(disk, ResumeProblem.FILE_MISSING))
            world.resumeCalls() shouldBe emptyList()
        }

    @Test
    fun `missing start directory is DIRECTORY_MISSING without calling the runtime`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1, cwd = sub)))
            world.missingDirs.add(sub)

            val result = world.commands.resume(disk)

            result shouldBe Outcome.Err(SessionError.ResumeUnavailable(disk, ResumeProblem.DIRECTORY_MISSING))
            world.resumeCalls() shouldBe emptyList()
        }

    @Test
    fun `a missing file wins over a missing directory`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1, cwd = sub)))
            world.history.delete(conversationOf(1))
            world.missingDirs.add(sub)

            world.commands.resume(disk) shouldBe
                Outcome.Err(SessionError.ResumeUnavailable(disk, ResumeProblem.FILE_MISSING))
        }

    @Test
    fun `unreadable history is Runtime without calling the runtime`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1)))
            world.history.failNext(HistoryError.UNREADABLE)

            world.commands.resume(disk) shouldBe Outcome.Err(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))

            world.resumeCalls() shouldBe emptyList()
            world.diskIds() shouldBe listOf(uuidOf(1))
        }

    @Test
    fun `supervisor session is UnknownSession without calling the runtime`() =
        runTest {
            val supervisor = aRuntimeSession(id = SessionId("aaaa0001"), cwd = P1_DIR)
            val world = resumeWorld(listOf(conversation(1)), sessions = listOf(supervisor))

            world.commands.resume(supervisor.id) shouldBe Outcome.Err(SessionError.UnknownSession(supervisor.id))

            world.resumeCalls() shouldBe emptyList()
        }

    @Test
    fun `hidden disk session is UnknownSession`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1)))
            world.commands.remove(disk)

            world.commands.resume(disk) shouldBe Outcome.Err(SessionError.UnknownSession(disk))

            world.resumeCalls() shouldBe emptyList()
        }

    @Test
    fun `failed listing before resume is Runtime without calling the runtime`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1)))
            world.runtime.failListings = RuntimeError.Timeout

            world.commands.resume(disk) shouldBe Outcome.Err(SessionError.Runtime(RuntimeProblem.TIMEOUT))

            world.resumeCalls() shouldBe emptyList()
        }

    @Test
    fun `runtime failure keeps the disk row`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1)))
            world.runtime.fail("resume", RuntimeError.WorkspaceNotTrusted)

            world.commands.resume(disk) shouldBe
                Outcome.Err(SessionError.Runtime(RuntimeProblem.WORKSPACE_NOT_TRUSTED))

            world.diskIds() shouldBe listOf(uuidOf(1))
            world.supervisorIds() shouldBe emptyList()
        }

    @Test
    fun `listing of a failed resumed job wins over the expected state`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1)))
            world.fake.loses(conversationOf(1))

            world.commands.resume(disk) shouldBe Outcome.Ok(ResumedSession(live, copy = false))

            // the file vanished between the check and the start: the supervisor lists the job as failed
            world.session(live.value).state shouldBe SessionState.Failed
            world.diskIds() shouldBe emptyList()
        }

    @Test
    fun `second resume of the same session is UnknownSession and calls the runtime once`() =
        runTest {
            val world = resumeWorld(listOf(conversation(1)))
            world.commands.resume(disk)

            world.commands.resume(disk) shouldBe Outcome.Err(SessionError.UnknownSession(disk))

            world.resumeCalls() shouldBe listOf("resume ${uuidOf(1)}")
        }

    @Test
    fun `resume logs ids and categories only`() =
        runTest {
            val secretDir = P1_DIR.resolve("secret-folder")
            val world = resumeWorld(listOf(conversation(1, cwd = secretDir, title = "SECRET-TITLE")))
            world.commands.resume(disk)
            val failing = resumeWorld(listOf(conversation(1, cwd = secretDir, title = "SECRET-TITLE")))
            failing.runtime.fail("resume", RuntimeError.CommandFailed(1, "boom SECRET-TITLE"))
            failing.commands.resume(disk)

            val logged = world.logger.entries.toString() + failing.logger.entries.toString()
            assertSoftly {
                logged shouldNotContain "SECRET-TITLE"
                logged shouldNotContain "secret-folder"
                logged shouldNotContain "boom"
            }
        }

    private companion object {
        const val SHORT_ID = 8
    }
}
