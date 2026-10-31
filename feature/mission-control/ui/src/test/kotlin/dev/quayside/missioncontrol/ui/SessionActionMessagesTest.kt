package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Fixed texts and log categories of a failed stop or respawn (plan, table 2; ENG-C8). */
internal class SessionActionMessagesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `error gives a fixed text and a log category`(
        @Suppress("UNUSED_PARAMETER") name: String,
        error: SessionError,
        key: String,
        category: String,
    ) {
        SessionActionMessages.reason(error) shouldBe UiText(key)
        SessionActionMessages.category(error) shouldBe category
        MissionControlStrings[key].shouldNotBeBlank()
    }

    @Test
    fun `texts never contain the session id`() {
        val secret = SessionId("secret-session")
        val errors =
            listOf(
                SessionError.UnknownSession(secret),
                SessionError.SessionActive(secret),
                SessionError.UnknownProject(ProjectId("secret-project")),
                SessionError.StopIncomplete(stopped = 1, failed = listOf(secret)),
            ) + RuntimeProblem.entries.map { SessionError.Runtime(it) }
        errors.forEach { error ->
            val text = SessionActionMessages.reason(error)
            withClue(error) {
                text.args shouldBe emptyList()
                MissionControlStrings.format(text) shouldNotContain "secret"
                SessionActionMessages.category(error) shouldNotContain "secret"
            }
        }
    }

    @Test
    fun `every runtime problem has its own text`() {
        val texts = RuntimeProblem.entries.map { SessionActionMessages.reason(SessionError.Runtime(it)).key }

        texts.toSet().size shouldBe RuntimeProblem.entries.size
    }

    companion object {
        @JvmStatic
        fun cases(): List<Arguments> = fixedCases() + runtimeCases()

        private fun fixedCases(): List<Arguments> =
            listOf(
                arguments(
                    "unknown session",
                    SessionError.UnknownSession(SessionId("a")),
                    "action.reason.unknownSession",
                    "unknownSession",
                ),
                arguments(
                    "session active",
                    SessionError.SessionActive(SessionId("a")),
                    "action.reason.sessionActive",
                    "sessionActive",
                ),
                arguments(
                    "unknown project",
                    SessionError.UnknownProject(ProjectId("p")),
                    "action.reason.other",
                    "unknownProject",
                ),
                arguments(
                    "stop incomplete",
                    SessionError.StopIncomplete(stopped = 1, failed = listOf(SessionId("a"))),
                    "action.reason.other",
                    "stopIncomplete",
                ),
            )

        private fun runtimeCases(): List<Arguments> =
            RuntimeProblem.entries.map { problem ->
                arguments(
                    "runtime $problem",
                    SessionError.Runtime(problem),
                    "action.reason.runtime.${problem.name}",
                    "runtime.${problem.name}",
                )
            }
    }
}
