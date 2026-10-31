package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The runner calls one command, logs the session id and a fixed category only, and returns the failure text. */
internal class SessionActionRunnerTest {
    private val id = SessionId("abc12345")
    private val secretName = "secret-name"
    private val log = RecordingLogger()

    private fun fake(state: SessionState): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects = emptyList(),
                sessions = listOf(WorldSession(id, A_PROJECT_DIR.resolve("secret-dir"), state, name = secretName)),
            ),
        )

    @Test
    fun `stop calls stop and logs info with the session id`() =
        runTest {
            val sessions = fake(SessionState.Working)

            SessionActionRunner(sessions, log).run(SessionAction.STOP, id) shouldBe null

            sessions.calls shouldBe listOf("stop abc12345")
            log.entries.map { it.level to it.message } shouldBe listOf(LogLevel.INFO to "session stopped")
            log.entries.single().fields shouldBe mapOf("sessionId" to "abc12345")
        }

    @Test
    fun `respawn calls respawn and logs info with the session id`() =
        runTest {
            val sessions = fake(SessionState.Stopped)

            SessionActionRunner(sessions, log).run(SessionAction.RESPAWN, id) shouldBe null

            sessions.calls shouldBe listOf("respawn abc12345")
            log.entries.map { it.level to it.message } shouldBe listOf(LogLevel.INFO to "session respawned")
            log.entries.single().fields shouldBe mapOf("sessionId" to "abc12345")
        }

    @Test
    fun `a failed stop returns the reason and logs a warning with the category`() =
        runTest {
            val sessions = fake(SessionState.Working)
            sessions.failNext(SessionError.Runtime(RuntimeProblem.TIMEOUT))

            val text = SessionActionRunner(sessions, log).run(SessionAction.STOP, id)

            text shouldBe UiText("action.reason.runtime.TIMEOUT")
            log.entries.map { it.level to it.message } shouldBe listOf(LogLevel.WARN to "session stop failed")
            log.entries.single().fields shouldBe mapOf("sessionId" to "abc12345", "reason" to "runtime.TIMEOUT")
        }

    @Test
    fun `a failed respawn returns the reason and logs a warning with the category`() =
        runTest {
            val sessions = fake(SessionState.Working)

            val text = SessionActionRunner(sessions, log).run(SessionAction.RESPAWN, id)

            text shouldBe UiText("action.reason.sessionActive")
            log.entries.map { it.level to it.message } shouldBe listOf(LogLevel.WARN to "session respawn failed")
            log.entries.single().fields shouldBe mapOf("sessionId" to "abc12345", "reason" to "sessionActive")
        }

    @Test
    fun `remove calls remove and logs info with the session id`() =
        runTest {
            val sessions = fake(SessionState.Done)

            SessionActionRunner(sessions, log).run(SessionAction.REMOVE, id) shouldBe null

            sessions.calls shouldBe listOf("remove abc12345")
            sessions.snapshot.value.sessions
                .shouldBeEmpty()
            log.entries.map { it.level to it.message } shouldBe listOf(LogLevel.INFO to "session removed")
            log.entries.single().fields shouldBe mapOf("sessionId" to "abc12345")
        }

    @Test
    fun `a failed remove returns the reason and logs a warning with the category`() =
        runTest {
            val sessions = fake(SessionState.Working)

            val text = SessionActionRunner(sessions, log).run(SessionAction.REMOVE, id)

            text shouldBe UiText("action.reason.sessionActive")
            log.entries.map { it.level to it.message } shouldBe listOf(LogLevel.WARN to "session remove failed")
            log.entries.single().fields shouldBe mapOf("sessionId" to "abc12345", "reason" to "sessionActive")
        }

    @Test
    fun `logs of remove never hold the name or the directory of the session`() =
        runTest {
            val sessions = fake(SessionState.Done)
            val runner = SessionActionRunner(sessions, log)

            runner.run(SessionAction.REMOVE, id)
            runner.run(SessionAction.REMOVE, id)

            val logged = log.entries.joinToString { "${it.message} ${it.fields} ${it.cause}" }
            logged shouldNotContain secretName
            logged shouldNotContain "secret-dir"
            log.entries.size shouldBe 2
        }

    @Test
    fun `logs never hold the name or the directory of the session`() =
        runTest {
            val sessions = fake(SessionState.Working)
            val runner = SessionActionRunner(sessions, log)

            runner.run(SessionAction.STOP, id)
            runner.run(SessionAction.STOP, SessionId("missing"))
            runner.run(SessionAction.RESPAWN, id)

            val logged = log.entries.joinToString { "${it.message} ${it.fields} ${it.cause}" }
            logged shouldNotContain secretName
            logged shouldNotContain "secret-dir"
            log.entriesAt(LogLevel.DEBUG).shouldBeEmpty()
            log.entries.size shouldBe 3
        }
}
