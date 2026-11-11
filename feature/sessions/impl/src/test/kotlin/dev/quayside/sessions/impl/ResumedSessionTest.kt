package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.RuntimePhase
import dev.quayside.agentruntime.api.WaitingFor
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.sessions.api.pastSession
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

/** `ResumedSessions.effect`: the table of the AM-313 plan. Pure, so no coroutines. */
class ResumedSessionTest {
    private val uuid = ConversationId("00000001-0000-4000-8000-000000000001")
    private val cwd = Path.of("/Users/test/projects/p1/src")
    private val disk = pastSession(id = uuid.value, cwd = cwd, name = "Fix the login bug")
    private val now = T0.plusSeconds(90)

    @ParameterizedTest(name = "id {0} -> copy {1}, conversationId kept {2}")
    @CsvSource(
        "00000001, false, true",
        "00000002, true, false",
        "0000000, true, false",
        "00000001x, true, false",
        "1, true, false",
    )
    fun `the id decides between the same conversation and a copy`(
        id: String,
        copy: Boolean,
        kept: Boolean,
    ) {
        val effect = ResumedSessions.effect(disk, uuid, SessionId(id), now)

        assertSoftly {
            effect.copy shouldBe copy
            effect.session.conversationId shouldBe uuid.takeIf { kept }
            effect.session.id shouldBe SessionId(id)
        }
    }

    @ParameterizedTest(name = "id {0}")
    @CsvSource("00000001", "00000002")
    fun `the effect always waits for input in the start directory`(id: String) {
        val session = ResumedSessions.effect(disk, uuid, SessionId(id), now).session

        assertSoftly {
            session.phase shouldBe RuntimePhase.BLOCKED
            session.waitingFor shouldBe WaitingFor.Input
            session.cwd shouldBe cwd
            session.name shouldBe "Fix the login bug"
            session.startedAt shouldBe now
            session.pid shouldBe null
        }
    }

    @Test
    fun `the short id is compared to the first 8 characters of the UUID, not to the whole id`() {
        // a UUID that starts with 8 characters equal to those of another conversation must not match it
        val other = ConversationId("00000002-0000-4000-8000-000000000001")

        ResumedSessions.effect(disk, other, SessionId("00000001"), now).copy shouldBe true
    }
}
