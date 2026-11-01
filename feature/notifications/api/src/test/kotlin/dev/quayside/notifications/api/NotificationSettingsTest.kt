package dev.quayside.notifications.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class NotificationSettingsTest {
    @Test
    fun `defaults follow the table of docs 07`() {
        val s = NotificationSettings()
        val all = NotificationChannel.entries.toSet()

        s.channelsFor(NotificationKind.NEEDS_INPUT) shouldBe all
        s.channelsFor(NotificationKind.PERMISSION) shouldBe all
        s.channelsFor(NotificationKind.FAILED) shouldBe all
        s.channelsFor(NotificationKind.TURN_ENDED) shouldBe setOf(NotificationChannel.POPUP)
        s.channelsFor(NotificationKind.PULL_REQUEST) shouldBe
            setOf(NotificationChannel.POPUP, NotificationChannel.DOCK_BADGE)
        s.channelsFor(NotificationKind.RUNNING_LONG) shouldBe setOf(NotificationChannel.VOICE)
        s.channelsFor(NotificationKind.TERMINAL_BELL) shouldBe setOf(NotificationChannel.POPUP)
        s.runningLongMinutes shouldBe 30
    }

    @Test
    fun `an entry in the matrix replaces the default of its kind only`() {
        val s = NotificationSettings(matrix = mapOf(NotificationKind.FAILED to emptySet()))

        s.channelsFor(NotificationKind.FAILED) shouldBe emptySet()
        s.channelsFor(NotificationKind.NEEDS_INPUT) shouldBe NotificationChannel.entries.toSet()
    }

    @Test
    fun `withChannel turns a channel off and on again`() {
        val off = NotificationSettings().withChannel(NotificationKind.FAILED, NotificationChannel.SOUND, false)
        val on = off.withChannel(NotificationKind.FAILED, NotificationChannel.SOUND, true)

        off.channelsFor(NotificationKind.FAILED) shouldBe
            NotificationChannel.entries.toSet() - NotificationChannel.SOUND
        on.channelsFor(NotificationKind.FAILED) shouldBe NotificationChannel.entries.toSet()
    }

    @Test
    fun `popup defaults follow docs 07`() {
        NotificationSettings().popup shouldBe
            PopupSettings(PopupPosition.BOTTOM_RIGHT, PopupDuration.QUESTIONS_STAY, 3, PopupStyle.DETAILED)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 11])
    fun `popup count outside the range is rejected`(count: Int) {
        shouldThrow<IllegalArgumentException> { PopupSettings(maxVisible = count) }
    }

    @Test
    fun `popup model hides user data in toString`() {
        val popup =
            PopupModel(
                PopupId("p"),
                NotificationKind.NEEDS_INPUT,
                null,
                "Secret project",
                "secret-name",
                "secret?",
                true,
                false,
            )

        popup.toString().contains("secret") shouldBe false
        popup.toString().contains("Secret") shouldBe false
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, 1441])
    fun `threshold outside the range is rejected`(minutes: Int) {
        shouldThrow<IllegalArgumentException> { NotificationSettings(runningLongMinutes = minutes) }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 30, 1440])
    fun `threshold inside the range is accepted`(minutes: Int) {
        NotificationSettings(runningLongMinutes = minutes).runningLongMinutes shouldBe minutes
    }
}
