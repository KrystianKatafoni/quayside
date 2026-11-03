package dev.quayside.notifications.impl

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.notifications.api.NotificationKind
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** [VoiceGrouping]: at most one message per 20 s, the rest merged (FR-N4). Time is the caller's. */
class VoiceGroupingTest {
    private fun message(
        session: String?,
        text: String,
        kind: NotificationKind = NotificationKind.NEEDS_INPUT,
        project: String? = null,
    ) = VoiceMessage(session?.let(::SessionId), kind, text, project?.let(::ProjectId))

    private fun VoiceGrouping.offerAll(vararg messages: VoiceMessage): VoiceGrouping =
        messages.fold(this) { state, message -> state.offer(message).state }

    @Test
    fun `first message is spoken at once`() {
        val step = VoiceGrouping().offer(message("a", "A czeka"))

        step.action shouldBe VoiceAction.Speak("A czeka")
        step.state.cooling shouldBe true
        step.state.pending shouldBe emptyList()
    }

    @Test
    fun `message during cooldown waits`() {
        val first = VoiceGrouping().offer(message("a", "A czeka")).state

        val step = first.offer(message("b", "B czeka"))

        step.action shouldBe VoiceAction.Stay
        step.state.cooling shouldBe true
        step.state.pending shouldBe listOf(message("b", "B czeka"))
    }

    @Test
    fun `cooldown with nothing pending ends`() {
        val step = VoiceGrouping(cooling = true).cooldownEnded()

        step.action shouldBe VoiceAction.Stay
        step.state shouldBe VoiceGrouping()
    }

    @Test
    fun `after the cooldown the next message is spoken at once again`() {
        val idle = VoiceGrouping(cooling = true).cooldownEnded().state

        idle.offer(message("c", "C czeka")).action shouldBe VoiceAction.Speak("C czeka")
    }

    @Test
    fun `one pending message is spoken as it is and starts a new cooldown`() {
        val waiting = VoiceGrouping(cooling = true).offerAll(message("b", "B czeka"))

        val step = waiting.cooldownEnded()

        step.action shouldBe VoiceAction.Speak("B czeka")
        step.state shouldBe VoiceGrouping(cooling = true)
    }

    @Test
    fun `two sessions are grouped`() {
        val waiting = VoiceGrouping(cooling = true).offerAll(message("b", "B czeka"), message("c", "C czeka"))

        val step = waiting.cooldownEnded()

        step.action shouldBe VoiceAction.Speak("2 agenty czekają")
        step.state shouldBe VoiceGrouping(cooling = true)
    }

    @Test
    fun `three sessions are grouped with the count of sessions`() {
        val waiting =
            VoiceGrouping(cooling = true).offerAll(
                message("b", "B czeka"),
                message("c", "C czeka", NotificationKind.FAILED),
                message("d", "D czeka", NotificationKind.PERMISSION),
            )

        waiting.cooldownEnded().action shouldBe VoiceAction.Speak("3 agenty czekają")
    }

    @Test
    fun `same session and kind keeps the newest`() {
        val waiting = VoiceGrouping(cooling = true).offerAll(message("b", "stare"), message("b", "nowe"))

        waiting.pending shouldBe listOf(message("b", "nowe"))
        waiting.cooldownEnded().action shouldBe VoiceAction.Speak("nowe")
    }

    @Test
    fun `one session two kinds speaks the newest`() {
        val waiting =
            VoiceGrouping(cooling = true).offerAll(
                message("b", "pyta", NotificationKind.NEEDS_INPUT),
                message("b", "błąd", NotificationKind.FAILED),
            )

        waiting.cooldownEnded().action shouldBe VoiceAction.Speak("błąd")
    }

    @Test
    fun `a replaced message does not add a session to the count`() {
        val waiting =
            VoiceGrouping(cooling = true).offerAll(
                message("b", "B1"),
                message("c", "C1"),
                message("b", "B2"),
            )

        waiting.cooldownEnded().action shouldBe VoiceAction.Speak("2 agenty czekają")
    }

    @Test
    fun `messages without a session count as one more source`() {
        val waiting = VoiceGrouping(cooling = true).offerAll(message(null, "Dzwonek"), message("b", "B czeka"))

        waiting.cooldownEnded().action shouldBe VoiceAction.Speak("2 agenty czekają")
    }

    @Test
    fun `a message spoken at once keeps its project`() {
        val step = VoiceGrouping().offer(message("a", "A czeka", project = "p1"))

        step.action shouldBe VoiceAction.Speak("A czeka", ProjectId("p1"))
    }

    @Test
    fun `a message without a project is spoken without one`() {
        VoiceGrouping().offer(message(null, "Dzwonek")).action shouldBe VoiceAction.Speak("Dzwonek", null)
    }

    @Test
    fun `group of one project keeps the project`() {
        val oneSession = VoiceGrouping(cooling = true).offerAll(message("b", "B czeka", project = "p1"))
        val twoSessions =
            VoiceGrouping(cooling = true).offerAll(
                message("b", "B czeka", project = "p1"),
                message("c", "C czeka", project = "p1"),
            )

        oneSession.cooldownEnded().action shouldBe VoiceAction.Speak("B czeka", ProjectId("p1"))
        twoSessions.cooldownEnded().action shouldBe VoiceAction.Speak("2 agenty czekają", ProjectId("p1"))
    }

    @Test
    fun `group of two projects has no project`() {
        val waiting =
            VoiceGrouping(cooling = true).offerAll(
                message("b", "B czeka", project = "p1"),
                message("c", "C czeka", project = "p2"),
            )

        waiting.cooldownEnded().action shouldBe VoiceAction.Speak("2 agenty czekają", null)
    }

    @Test
    fun `group of a project and a session without a project has no project`() {
        val waiting =
            VoiceGrouping(cooling = true).offerAll(
                message("b", "B czeka", project = "p1"),
                message("c", "C czeka"),
            )

        waiting.cooldownEnded().action shouldBe VoiceAction.Speak("2 agenty czekają", null)
    }

    @Test
    fun `a replaced message does not keep the old project in the group`() {
        val waiting =
            VoiceGrouping(cooling = true).offerAll(
                message("b", "B1", project = "p2"),
                message("c", "C1", project = "p1"),
                message("b", "B2", project = "p1"),
            )

        waiting.cooldownEnded().action shouldBe VoiceAction.Speak("2 agenty czekają", ProjectId("p1"))
    }

    @ParameterizedTest(name = "{0} sessions")
    @CsvSource(
        "2,2 agenty czekają",
        "3,3 agenty czekają",
        "4,4 agenty czekają",
        "5,5 agentów czeka",
        "11,11 agentów czeka",
        "12,12 agentów czeka",
        "14,14 agentów czeka",
        "15,15 agentów czeka",
        "21,21 agentów czeka",
        "22,22 agenty czekają",
        "24,24 agenty czekają",
        "25,25 agentów czeka",
        "112,112 agentów czeka",
    )
    fun `groupedText follows Polish plural rules`(
        count: Int,
        expected: String,
    ) {
        groupedText(count) shouldBe expected
    }
}
