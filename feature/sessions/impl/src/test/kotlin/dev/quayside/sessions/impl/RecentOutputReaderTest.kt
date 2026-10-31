package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.FakeAgentRuntime
import dev.quayside.agentruntime.api.RuntimeError
import dev.quayside.agentruntime.api.RuntimeWorld
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.sessions.api.RecentOutput
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.impl.MonitorFixtures.S1
import dev.quayside.sessions.impl.MonitorFixtures.working
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** AM-411: [RecentOutputReader] reads the tail of a session output from the runtime and maps its errors. */
class RecentOutputReaderTest {
    private val id = SessionId(S1)
    private val logger = RecordingLogger()
    private val fake =
        FakeAgentRuntime(RuntimeWorld(sessions = listOf(working(S1)), outputs = mapOf(id to "secret-line")))
    private val reader = RecentOutputReader(fake, logger)

    @ParameterizedTest(name = "{0}")
    @MethodSource("errors")
    fun `maps every runtime error`(
        error: RuntimeError,
        expected: SessionError,
    ) = runTest {
        fake.failNext(error)

        reader.read(id) shouldBe Outcome.Err(expected)
    }

    @Test
    fun `reads the output as it is`() =
        runTest {
            reader.read(id) shouldBe Outcome.Ok(RecentOutput("secret-line"))
        }

    @Test
    fun `reads without listing sessions`() =
        runTest {
            reader.read(id)

            fake.calls shouldBe listOf("recentOutput $S1")
        }

    @Test
    fun `failure logs the session id and problem only`() =
        runTest {
            fake.failNext(RuntimeError.CommandFailed(exitCode = 1, stderrTail = "secret-tail"))

            reader.read(id)

            val warnings = logger.entriesAt(LogLevel.WARN)
            warnings.size shouldBe 1
            warnings.single().message shouldBe "Session output failed"
            warnings.single().fields shouldBe mapOf("session" to S1, "problem" to RuntimeProblem.COMMAND_FAILED)
            logger.entries.forEach { it.toString() shouldNotContain "secret" }
        }

    @Test
    fun `success logs nothing`() =
        runTest {
            reader.read(id)

            logger.entries.shouldBeEmpty()
        }

    companion object {
        @JvmStatic
        fun errors(): Stream<Arguments> =
            Stream.of(
                arguments(
                    RuntimeError.UnknownSession(SessionId("zzzz0001")),
                    SessionError.UnknownSession(SessionId("zzzz0001")),
                ),
                arguments(RuntimeError.CommandFailed(1, "x"), SessionError.Runtime(RuntimeProblem.COMMAND_FAILED)),
                arguments(RuntimeError.Timeout, SessionError.Runtime(RuntimeProblem.TIMEOUT)),
                arguments(RuntimeError.Unparseable("x"), SessionError.Runtime(RuntimeProblem.UNPARSEABLE)),
                arguments(RuntimeError.NotInstalled, SessionError.Runtime(RuntimeProblem.NOT_INSTALLED)),
                arguments(
                    RuntimeError.UnsupportedVersion("1", "2"),
                    SessionError.Runtime(RuntimeProblem.UNSUPPORTED_VERSION),
                ),
                arguments(RuntimeError.AgentViewDisabled, SessionError.Runtime(RuntimeProblem.AGENT_VIEW_DISABLED)),
            )
    }
}
