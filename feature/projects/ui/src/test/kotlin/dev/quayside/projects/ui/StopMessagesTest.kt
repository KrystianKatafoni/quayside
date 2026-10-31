package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Fixed texts and log categories of a failed "stop sessions and remove" (FR-P4; AM-305 plan, decision 3). */
class StopMessagesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `error gives a fixed text and a log category`(
        @Suppress("UNUSED_PARAMETER") name: String,
        error: SessionError,
        key: String,
        category: String,
    ) {
        StopMessages.of(error) shouldBe UiText(key)
        StopMessages.category(error) shouldBe category
    }

    @Test
    fun `texts never contain session ids`() {
        val secret = SessionId("secret-session")
        val errors =
            listOf(
                SessionError.StopIncomplete(stopped = 1, failed = listOf(secret)),
                SessionError.UnknownSession(secret),
                SessionError.SessionActive(secret),
                SessionError.UnknownProject(ProjectId("secret-project")),
            )
        errors.forEach { error ->
            val text = StopMessages.of(error)
            withClue(error) {
                text.args shouldBe emptyList()
                ProjectsStrings.format(text) shouldNotContain "secret"
                StopMessages.category(error) shouldNotContain "secret"
            }
        }
    }

    companion object {
        @JvmStatic
        @Suppress("LongMethod")
        fun cases(): List<Arguments> =
            listOf(
                arguments(
                    "stop incomplete",
                    SessionError.StopIncomplete(stopped = 1, failed = listOf(SessionId("a"))),
                    "remove.stopFailed",
                    "stopIncomplete",
                ),
                arguments(
                    "unknown project",
                    SessionError.UnknownProject(ProjectId("p")),
                    "remove.stopFailed",
                    "unknownProject",
                ),
                arguments(
                    "unknown session",
                    SessionError.UnknownSession(SessionId("a")),
                    "remove.stopFailed",
                    "unknownSession",
                ),
                arguments(
                    "session active",
                    SessionError.SessionActive(SessionId("a")),
                    "remove.stopFailed",
                    "sessionActive",
                ),
            ) +
                RuntimeProblem.entries.map { problem ->
                    arguments(
                        "runtime $problem",
                        SessionError.Runtime(problem),
                        "remove.runtimeFailed",
                        "runtime.${problem.name}",
                    )
                }
    }
}
