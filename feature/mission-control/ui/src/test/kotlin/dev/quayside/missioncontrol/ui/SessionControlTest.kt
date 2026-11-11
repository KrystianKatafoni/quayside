package dev.quayside.missioncontrol.ui

import dev.quayside.core.designsystem.ButtonKind
import dev.quayside.core.designsystem.IconKind
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** The buttons of the details panel and the items of the row menu (plan AM-809, C hierarchy). */
internal class SessionControlTest {
    private fun details(
        state: SessionState,
        actions: Set<SessionAction>,
        canReply: Boolean,
    ) = SessionDetails(
        id = SessionId("one"),
        name = "fix-login",
        glyph = StatusLook.of(state),
        summary = null,
        worktree = Path.of("/Users/test/project"),
        startedAt = T0,
        lastActivityAt = T0,
        actions = actions,
        canAttach = true,
        canReply = canReply,
    )

    private val waiting = details(SessionState.AwaitingInput("q"), setOf(SessionAction.STOP), canReply = true)
    private val finished =
        details(SessionState.Done, setOf(SessionAction.RESPAWN, SessionAction.REMOVE), canReply = false)

    @Test
    fun `details controls go primary then secondary then danger`() {
        val controls = waiting.detailsControls()

        controls.map { it.id } shouldBe listOf("reply", "attach", "preview", "stop")
        controls.map { it.kind } shouldBe
            listOf(ButtonKind.PRIMARY, ButtonKind.SECONDARY, ButtonKind.SECONDARY, ButtonKind.DANGER)
    }

    @Test
    fun `finished session keeps remove last and dangerous`() {
        val controls = finished.detailsControls()

        controls.map { it.id }.toSet() shouldBe setOf("attach", "respawn", "preview", "remove")
        controls.last().id shouldBe "remove"
        controls.last().kind shouldBe ButtonKind.DANGER
        controls.map { it.kind }.sorted() shouldBe controls.map { it.kind }
        controls.single { it.id == "respawn" }.kind shouldBe ButtonKind.SECONDARY
    }

    @Test
    fun `row menu order keeps the old controls and puts resume before remove`() {
        val all = details(SessionState.AwaitingInput("q"), SessionAction.entries.toSet(), canReply = true)

        all.allControls().map { it.id } shouldBe
            listOf("attach", "reply", "stop", "respawn", "resume", "remove", "preview")
    }

    @Test
    fun `controls carry their icons`() {
        val all = details(SessionState.AwaitingInput("q"), SessionAction.entries.toSet(), canReply = true)

        all.allControls().filter { it.id != "preview" }.associate { it.id to it.icon } shouldBe
            mapOf(
                "attach" to IconKind.ATTACH,
                "reply" to IconKind.REPLY,
                "stop" to IconKind.STOP,
                "respawn" to IconKind.RESPAWN,
                "resume" to IconKind.RESUME,
                "remove" to IconKind.REMOVE,
            )
    }
}
