package dev.quayside.notifications.impl

import dev.quayside.core.domain.SessionId
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.PopupDuration
import dev.quayside.notifications.api.PopupId
import dev.quayside.notifications.api.PopupModel
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The pure rules of the popup stack (FR-N3). */
class PopupRulesTest {
    private fun popup(
        id: String,
        session: String? = id,
        kind: NotificationKind = NotificationKind.FAILED,
    ) = PopupModel(PopupId(id), kind, session?.let(::SessionId), null, null, null, sticky = false, collapsed = false)

    @ParameterizedTest(name = "{0} under {1} stays: {2}")
    @MethodSource("stickiness")
    fun `stickiness follows the duration setting`(
        kind: NotificationKind,
        duration: PopupDuration,
        expected: Boolean,
    ) {
        PopupRules.sticky(kind, duration) shouldBe expected
    }

    @Test
    fun `detail is the question or the tool`() {
        PopupRules.detailOf(SessionState.AwaitingInput("ok?")) shouldBe "ok?"
        PopupRules.detailOf(SessionState.AwaitingPermission("Bash")) shouldBe "Bash"
        PopupRules.detailOf(SessionState.AwaitingInput(null)) shouldBe null
        PopupRules.detailOf(SessionState.Done) shouldBe null
        PopupRules.detailOf(null) shouldBe null
    }

    @Test
    fun `the newest popup comes first and the extra ones collapse`() {
        val stacked = PopupRules.stack(popup("d"), listOf(popup("c"), popup("b"), popup("a")), maxVisible = 2)

        stacked.map { it.id.value } shouldBe listOf("d", "c", "b", "a")
        stacked.map { it.collapsed } shouldBe listOf(false, false, true, true)
    }

    @Test
    fun `popups beyond the collapsed lines are dropped`() {
        val older = (1..10).map { popup("o$it") }

        PopupRules.stack(popup("new"), older, maxVisible = 1).size shouldBe 1 + PopupRules.MAX_COLLAPSED
    }

    @Test
    fun `a popup replaces the older one of the same session and kind`() {
        val older = listOf(popup("x2", session = "s"), popup("y", session = "t"))

        val stacked = PopupRules.stack(popup("x3", session = "s"), older, maxVisible = 3)

        stacked.map { it.id.value } shouldBe listOf("x3", "y")
    }

    @Test
    fun `popups of the same session but another kind stay`() {
        val older = listOf(popup("x2", session = "s", kind = NotificationKind.TURN_ENDED))

        PopupRules.stack(popup("x3", session = "s"), older, maxVisible = 3).size shouldBe 2
    }

    @Test
    fun `popups without a session never replace each other`() {
        val stacked = PopupRules.stack(popup("b2", session = null), listOf(popup("b1", session = null)), 3)

        stacked.size shouldBe 2
    }

    @Test
    fun `arranging after a removal expands a collapsed popup again`() {
        val arranged = PopupRules.arrange(listOf(popup("a").copy(collapsed = true)), maxVisible = 1)

        arranged.single().collapsed shouldBe false
    }

    companion object {
        @JvmStatic
        fun stickiness(): List<Arguments> =
            listOf(
                arguments(NotificationKind.NEEDS_INPUT, PopupDuration.QUESTIONS_STAY, true),
                arguments(NotificationKind.PERMISSION, PopupDuration.QUESTIONS_STAY, true),
                arguments(NotificationKind.TURN_ENDED, PopupDuration.QUESTIONS_STAY, false),
                arguments(NotificationKind.FAILED, PopupDuration.QUESTIONS_STAY, false),
                arguments(NotificationKind.TURN_ENDED, PopupDuration.ALWAYS_TIMED, false),
                arguments(NotificationKind.NEEDS_INPUT, PopupDuration.ALWAYS_TIMED, false),
                arguments(NotificationKind.TURN_ENDED, PopupDuration.ALWAYS_STAY, true),
            )
    }
}
