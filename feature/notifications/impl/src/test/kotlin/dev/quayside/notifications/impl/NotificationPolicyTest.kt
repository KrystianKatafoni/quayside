package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationChannel.DOCK_BADGE
import dev.quayside.notifications.api.NotificationChannel.POPUP
import dev.quayside.notifications.api.NotificationChannel.SOUND
import dev.quayside.notifications.api.NotificationChannel.VOICE
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration.Companion.minutes

/** `decide()`: event × settings → deliveries (FR-N1, FR-N2). */
class NotificationPolicyTest {
    private val settings = NotificationSettings()

    private fun channels(
        event: SessionEvent,
        settings: NotificationSettings = this.settings,
    ): List<NotificationChannel> = decide(event, settings).map { it.channel }

    private fun waiting(state: SessionState) = SessionEvent.BecameBlocked(aSession(state = state))

    @ParameterizedTest(name = "{0}")
    @MethodSource("defaults")
    fun `default settings deliver as in docs 07`(
        label: String,
        event: SessionEvent,
        expected: List<NotificationChannel>,
    ) {
        label.isNotEmpty() shouldBe true
        channels(event) shouldContainExactly expected
    }

    @Test
    fun `a delivery names its kind and the session`() {
        val event = SessionEvent.Failed(aSession(state = SessionState.Failed))

        decide(event, settings).forEach {
            it.kind shouldBe NotificationKind.FAILED
            it.session shouldBe event.session
        }
    }

    @Test
    fun `a blocked event whose state is not waiting delivers nothing`() {
        decide(waiting(SessionState.Working), settings).shouldBeEmpty()
    }

    @Test
    fun `permission without a known tool still delivers`() {
        channels(waiting(SessionState.AwaitingPermission(null))) shouldContainExactly
            listOf(POPUP, VOICE, SOUND, DOCK_BADGE)
    }

    @Test
    fun `switching popups off silences the popup of every kind`() {
        val quiet = settings.copy(popups = false)

        channels(waiting(SessionState.AwaitingInput("ok?")), quiet) shouldContainExactly
            listOf(VOICE, SOUND, DOCK_BADGE)
        channels(SessionEvent.TurnFinished(aSession(state = SessionState.Done)), quiet).shouldBeEmpty()
    }

    @Test
    fun `switching voice off silences the voice of every kind`() {
        val quiet = settings.copy(voice = false)

        channels(waiting(SessionState.AwaitingInput("ok?")), quiet) shouldContainExactly
            listOf(POPUP, SOUND, DOCK_BADGE)
        channels(SessionEvent.RunningLong(aSession(), 31.minutes), quiet).shouldBeEmpty()
    }

    @Test
    fun `the matrix decides the channels of one kind`() {
        val custom =
            settings
                .withChannel(
                    NotificationKind.TURN_ENDED,
                    SOUND,
                    true,
                ).withChannel(NotificationKind.TURN_ENDED, POPUP, false)

        channels(SessionEvent.TurnFinished(aSession(state = SessionState.Done)), custom) shouldContainExactly
            listOf(SOUND)
    }

    @Test
    fun `an empty entry mutes a kind`() {
        val muted = settings.copy(matrix = mapOf(NotificationKind.FAILED to emptySet()))

        channels(SessionEvent.Failed(aSession(state = SessionState.Failed)), muted).shouldBeEmpty()
    }

    @ParameterizedTest(name = "after {0} min")
    @MethodSource("longRunning")
    fun `running long counts from the threshold on`(
        minutes: Int,
        delivered: Boolean,
    ) {
        val event = SessionEvent.RunningLong(aSession(), minutes.minutes)

        channels(event, settings.copy(runningLongMinutes = 30)) shouldBe if (delivered) listOf(VOICE) else emptyList()
    }

    @Test
    fun `the threshold of the settings is used`() {
        val event = SessionEvent.RunningLong(aSession(), 10.minutes)

        channels(event, settings.copy(runningLongMinutes = 10)) shouldContainExactly listOf(VOICE)
        channels(event, settings.copy(runningLongMinutes = 11)).shouldBeEmpty()
    }

    companion object {
        @JvmStatic
        @Suppress("LongMethod")
        fun defaults(): List<Arguments> {
            val session = aSession()
            return listOf(
                arguments(
                    "waits for a reply",
                    SessionEvent.BecameBlocked(aSession(state = SessionState.AwaitingInput("ok?"))),
                    listOf(POPUP, VOICE, SOUND, DOCK_BADGE),
                ),
                arguments(
                    "asks for permission",
                    SessionEvent.BecameBlocked(aSession(state = SessionState.AwaitingPermission("Bash"))),
                    listOf(POPUP, VOICE, SOUND, DOCK_BADGE),
                ),
                arguments("turn ended", SessionEvent.TurnFinished(aSession(state = SessionState.Done)), listOf(POPUP)),
                arguments(
                    "failed",
                    SessionEvent.Failed(aSession(state = SessionState.Failed)),
                    listOf(POPUP, VOICE, SOUND, DOCK_BADGE),
                ),
                arguments("pull request", SessionEvent.PullRequestReady(session), listOf(POPUP, DOCK_BADGE)),
                arguments("long run", SessionEvent.RunningLong(session, 30.minutes), listOf(VOICE)),
            )
        }

        @JvmStatic
        fun longRunning(): List<Arguments> =
            listOf(
                arguments(29, false),
                arguments(30, true),
                arguments(90, true),
            )
    }
}
