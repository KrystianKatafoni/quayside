package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationChannel.DOCK_BADGE
import dev.quayside.notifications.api.NotificationChannel.POPUP
import dev.quayside.notifications.api.NotificationChannel.SOUND
import dev.quayside.notifications.api.NotificationChannel.VOICE
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.QuietHours
import dev.quayside.notifications.api.SpeechSettings
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
import java.time.LocalTime
import kotlin.time.Duration.Companion.minutes

/** `decide()`: event × settings → deliveries (FR-N1, FR-N2). */
class NotificationPolicyTest {
    private val settings = NotificationSettings()
    private val background = ctx()

    private fun channels(
        event: SessionEvent,
        settings: NotificationSettings = this.settings,
        context: DeliveryContext = background,
    ): List<NotificationChannel> = decide(event, settings, context).map { it.channel }

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

        decide(event, settings, background).forEach {
            it.kind shouldBe NotificationKind.FAILED
            it.session shouldBe event.session
        }
    }

    @Test
    fun `a blocked event whose state is not waiting delivers nothing`() {
        decide(waiting(SessionState.Working), settings, background).shouldBeEmpty()
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

    @Test
    fun `a terminal bell delivers on the popup by default and belongs to no session`() {
        val deliveries = decideTerminalBell(settings, background)

        deliveries.map { it.channel } shouldContainExactly listOf(POPUP)
        deliveries.forEach {
            it.kind shouldBe NotificationKind.TERMINAL_BELL
            it.session shouldBe null
        }
    }

    @Test
    fun `a terminal bell follows the matrix and the popup switch`() {
        val loud = settings.withChannel(NotificationKind.TERMINAL_BELL, SOUND, true)

        decideTerminalBell(loud, background).map { it.channel } shouldContainExactly listOf(POPUP, SOUND)
        decideTerminalBell(loud.copy(popups = false), background).map { it.channel } shouldContainExactly listOf(SOUND)
        decideTerminalBell(
            settings.copy(matrix = mapOf(NotificationKind.TERMINAL_BELL to emptySet())),
            background,
        ).shouldBeEmpty()
    }

    private val needsInput = waiting(SessionState.AwaitingInput("ok?"))

    @ParameterizedTest(name = "{0}")
    @MethodSource("voiceTable")
    fun `voice is allowed by the context and the switches`(
        label: String,
        speech: SpeechSettings,
        appActive: Boolean,
        microphoneActive: Boolean,
        allowed: Boolean,
    ) {
        label.isNotEmpty() shouldBe true
        voiceAllowed(speech, ctx(appActive, microphoneActive)) shouldBe allowed
    }

    @Test
    fun `the app in the foreground drops only voice`() {
        channels(needsInput, context = ctx(appActive = true)) shouldContainExactly
            listOf(POPUP, SOUND, DOCK_BADGE)
    }

    @Test
    fun `an active microphone drops only voice`() {
        channels(needsInput, context = ctx(microphoneActive = true)) shouldContainExactly
            listOf(POPUP, SOUND, DOCK_BADGE)
    }

    @Test
    fun `switches off keep voice in the foreground`() {
        val speech = SpeechSettings(onlyInBackground = false, muteWhenMicrophoneActive = false)
        val busy = ctx(appActive = true, microphoneActive = true)

        channels(needsInput, settings.copy(speech = speech), busy) shouldContainExactly
            listOf(POPUP, VOICE, SOUND, DOCK_BADGE)
    }

    @Test
    fun `the global voice switch still wins over the context`() {
        val speech = SpeechSettings(onlyInBackground = false, muteWhenMicrophoneActive = false)

        channels(needsInput, settings.copy(voice = false, speech = speech)) shouldContainExactly
            listOf(POPUP, SOUND, DOCK_BADGE)
    }

    @Test
    fun `terminal bell follows the same context`() {
        val loud = settings.withChannel(NotificationKind.TERMINAL_BELL, VOICE, true)

        decideTerminalBell(loud, background).map { it.channel } shouldContainExactly listOf(POPUP, VOICE)
        decideTerminalBell(loud, ctx(appActive = true))
            .map { it.channel } shouldContainExactly listOf(POPUP)
        decideTerminalBell(loud, ctx(microphoneActive = true))
            .map { it.channel } shouldContainExactly listOf(POPUP)
    }

    private val night = LocalTime.of(23, 0)
    private val quietOn = settings.copy(quietHours = QuietHours(enabled = true))

    @Test
    fun `quiet hours drop voice and sound but keep popup and badge`() {
        val atNight = ctx(localTime = night)

        channels(needsInput, quietOn, atNight) shouldContainExactly listOf(POPUP, DOCK_BADGE)
        channels(waiting(SessionState.AwaitingPermission("Bash")), quietOn, atNight) shouldContainExactly
            listOf(POPUP, DOCK_BADGE)
        channels(SessionEvent.Failed(aSession(state = SessionState.Failed)), quietOn, atNight) shouldContainExactly
            listOf(POPUP, DOCK_BADGE)
        channels(SessionEvent.RunningLong(aSession(), 31.minutes), quietOn, atNight).shouldBeEmpty()
        channels(needsInput, quietOn, ctx(localTime = LocalTime.NOON)) shouldContainExactly
            listOf(POPUP, VOICE, SOUND, DOCK_BADGE)
    }

    @Test
    fun `a Focus alone is quiet without hours and respectFocus off ignores it`() {
        val focus = ctx(focusActive = true)

        channels(needsInput, context = focus) shouldContainExactly listOf(POPUP, DOCK_BADGE)
        channels(needsInput, settings.copy(quietHours = QuietHours(respectFocus = false)), focus) shouldContainExactly
            listOf(POPUP, VOICE, SOUND, DOCK_BADGE)
    }

    @Test
    fun `quiet hours apply to the terminal bell`() {
        val loud =
            quietOn
                .withChannel(NotificationKind.TERMINAL_BELL, SOUND, true)
                .withChannel(NotificationKind.TERMINAL_BELL, VOICE, true)

        decideTerminalBell(loud, ctx(localTime = night)).map { it.channel } shouldContainExactly listOf(POPUP)
        decideTerminalBell(loud, ctx()).map { it.channel } shouldContainExactly listOf(POPUP, VOICE, SOUND)
    }

    @Test
    fun `default settings are never quiet at night`() {
        channels(needsInput, context = ctx(localTime = LocalTime.of(3, 0))) shouldContainExactly
            listOf(POPUP, VOICE, SOUND, DOCK_BADGE)
        channels(needsInput, context = ctx(localTime = night)) shouldContainExactly
            listOf(POPUP, VOICE, SOUND, DOCK_BADGE)
    }

    companion object {
        @JvmStatic
        fun voiceTable(): List<Arguments> {
            fun row(
                label: String,
                onlyInBackground: Boolean,
                muteWhenMicrophone: Boolean,
                appActive: Boolean,
                microphoneActive: Boolean,
                allowed: Boolean,
            ) = arguments(
                label,
                SpeechSettings(onlyInBackground = onlyInBackground, muteWhenMicrophoneActive = muteWhenMicrophone),
                appActive,
                microphoneActive,
                allowed,
            )
            return listOf(
                row("quiet desktop", true, true, false, false, true),
                row("app active, only in background", true, true, true, false, false),
                row("app active, switch off", false, true, true, false, true),
                row("microphone active, muting", true, true, false, true, false),
                row("microphone active, switch off", true, false, false, true, true),
                row("both active, both switches", true, true, true, true, false),
                row("both active, only app switch", true, false, true, true, false),
                row("both active, only microphone switch", false, true, true, true, false),
                row("both active, both switches off", false, false, true, true, true),
            )
        }

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
