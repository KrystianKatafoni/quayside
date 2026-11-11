package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.RecentOutput
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import dev.quayside.sessions.api.pastSession
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Pure rules of the output preview (AM-411 plan, decision 3 and table 1). */
internal class SessionPreviewTest {
    private val project = ProjectId("mine")
    private val sessions =
        listOf(
            aSession(id = "working", project = project, state = SessionState.Working),
            aSession(id = "done", project = project, state = SessionState.Done),
            aSession(id = "elsewhere", project = ProjectId("other"), state = SessionState.Working),
        )

    @ParameterizedTest(name = "{0}")
    @MethodSource("contents")
    fun `result of a read gives the content`(
        @Suppress("UNUSED_PARAMETER") name: String,
        outcome: Outcome<RecentOutput, SessionError>,
        expected: PreviewContent,
    ) {
        SessionPreview.content(outcome) shouldBe expected
    }

    @Test
    fun `text content hides the output in toString`() {
        PreviewContent.Text("secret output").toString() shouldNotContain "secret"
    }

    @Test
    fun `key is the selected visible session when the preview is open`() {
        SessionPreview.key(
            true,
            sessions,
            Selection(project = project, session = SessionId("working")),
            emptyList(),
        ) shouldBe
            SessionId("working")
    }

    @Test
    fun `disk session has a preview`() {
        val disk = pastSession(id = "00000000-0000-4000-8000-000000000001", project = project)

        SessionPreview.key(true, listOf(disk), Selection(project = project, session = disk.id), emptyList()) shouldBe
            disk.id
    }

    @Test
    fun `key is null when the preview is closed`() {
        SessionPreview.key(
            false,
            sessions,
            Selection(project = project, session = SessionId("working")),
            emptyList(),
        ) shouldBe
            null
    }

    @Test
    fun `key is null without a selected session`() {
        SessionPreview.key(true, sessions, Selection(project = project), emptyList()) shouldBe null
    }

    @Test
    fun `key is null for a session hidden by the filter`() {
        val selection = Selection(project = project, filter = SessionFilter.ACTIVE, session = SessionId("done"))

        SessionPreview.key(true, sessions, selection, emptyList()) shouldBe null
    }

    @Test
    fun `key is null for a session of another project`() {
        SessionPreview.key(
            true,
            sessions,
            Selection(project = project, session = SessionId("elsewhere")),
            emptyList(),
        ) shouldBe
            null
    }

    @Test
    fun `key follows the selection in a global view`() {
        val selection = Selection(view = GlobalView.WORKING, session = SessionId("elsewhere"))

        SessionPreview.key(true, sessions, selection, emptyList()) shouldBe SessionId("elsewhere")
    }

    companion object {
        @JvmStatic
        fun contents(): List<Arguments> =
            listOf(
                arguments("text", Outcome.Ok(RecentOutput("a\nb")), PreviewContent.Text("a\nb")),
                arguments("empty", Outcome.Ok(RecentOutput("")), PreviewContent.Empty),
                arguments("blank", Outcome.Ok(RecentOutput("  \n")), PreviewContent.Empty),
                arguments(
                    "unknown session",
                    Outcome.Err(SessionError.UnknownSession(SessionId("a"))),
                    PreviewContent.Failed(UiText("action.reason.unknownSession")),
                ),
                arguments(
                    "timeout",
                    Outcome.Err(SessionError.Runtime(RuntimeProblem.TIMEOUT)),
                    PreviewContent.Failed(UiText("action.reason.runtime.TIMEOUT")),
                ),
            )
    }
}
