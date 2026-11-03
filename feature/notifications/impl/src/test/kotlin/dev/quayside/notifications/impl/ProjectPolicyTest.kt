package dev.quayside.notifications.impl

import dev.quayside.core.domain.ProjectId
import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationChannel.DOCK_BADGE
import dev.quayside.notifications.api.NotificationChannel.POPUP
import dev.quayside.notifications.api.NotificationChannel.SOUND
import dev.quayside.notifications.api.NotificationChannel.VOICE
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationKind.FAILED
import dev.quayside.notifications.api.NotificationKind.NEEDS_INPUT
import dev.quayside.notifications.api.NotificationKind.PERMISSION
import dev.quayside.notifications.api.NotificationKind.PULL_REQUEST
import dev.quayside.notifications.api.NotificationKind.RUNNING_LONG
import dev.quayside.notifications.api.NotificationKind.TURN_ENDED
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.ProjectNotifications
import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.QuietHours
import dev.quayside.notifications.api.VoiceId
import dev.quayside.sessions.api.SessionEvent
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalTime
import kotlin.time.Duration.Companion.minutes

/** `decide()` with project exceptions (FR-N7): table 3 of the plan AM-705. */
internal class ProjectPolicyTest {
    private fun channels(
        event: SessionEvent,
        settings: NotificationSettings,
        context: DeliveryContext = ctx(),
    ): List<NotificationChannel> = decide(event, settings, context).map { it.channel }

    private fun withProfile(
        profile: ProjectProfile,
        base: NotificationSettings = NotificationSettings(),
    ) = base.copy(projects = mapOf(PROJECT to ProjectNotifications(profile)))

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("matrixTable")
    fun `profile narrows the default matrix`(
        profile: ProjectProfile,
        kind: NotificationKind,
        expected: List<NotificationChannel>,
    ) {
        channels(eventOf(kind), withProfile(profile)) shouldContainExactly expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filterTable")
    @Suppress("UnusedParameter")
    fun `profile combines with the matrix, switches, quiet hours and context`(
        label: String,
        settings: NotificationSettings,
        context: DeliveryContext,
        kind: NotificationKind,
        expected: List<NotificationChannel>,
    ) {
        channels(eventOf(kind), settings, context) shouldContainExactly expected
    }

    @Test
    fun `a session without a project is not narrowed by any entry`() {
        val settings = withProfile(ProjectProfile.MUTED)

        channels(eventOf(PERMISSION, project = null), settings) shouldContainExactly
            listOf(POPUP, VOICE, SOUND, DOCK_BADGE)
    }

    @Test
    fun `a project without an entry is not narrowed by the entry of another project`() {
        val settings = withProfile(ProjectProfile.MUTED)

        channels(eventOf(PERMISSION, project = OTHER), settings) shouldContainExactly
            listOf(POPUP, VOICE, SOUND, DOCK_BADGE)
        channels(eventOf(TURN_ENDED, project = OTHER), settings) shouldContainExactly listOf(POPUP)
    }

    @Test
    fun `an entry with only a voice changes no channel`() {
        val settings = NotificationSettings(projects = mapOf(PROJECT to ProjectNotifications(voice = VoiceId("Anna"))))

        NotificationKind.entries.filter { it != NotificationKind.TERMINAL_BELL }.forEach {
            channels(eventOf(it), settings) shouldContainExactly channels(eventOf(it), NotificationSettings())
        }
    }

    @Test
    fun `a terminal bell belongs to no project and ignores the muted entries`() {
        val settings = withProfile(ProjectProfile.MUTED)

        decideTerminalBell(settings, ctx()).map { it.channel } shouldContainExactly listOf(POPUP)
    }

    @Test
    fun `a terminal bell ignores the errors only entries too`() {
        val settings = withProfile(ProjectProfile.ERRORS_ONLY)

        decideTerminalBell(settings, ctx()).map { it.channel } shouldContainExactly listOf(POPUP)
    }

    @Test
    fun `the deliveries keep the session of the event`() {
        val event = eventOf(FAILED)

        decide(event, withProfile(ProjectProfile.ERRORS_ONLY), ctx()).forEach { it.session shouldBe event.session }
    }

    private fun eventOf(
        kind: NotificationKind,
        project: ProjectId? = PROJECT,
    ): SessionEvent =
        when (kind) {
            NEEDS_INPUT -> {
                SessionEvent.BecameBlocked(aSession(project = project, state = SessionState.AwaitingInput(null)))
            }

            PERMISSION -> {
                SessionEvent.BecameBlocked(aSession(project = project, state = SessionState.AwaitingPermission("Bash")))
            }

            TURN_ENDED -> {
                SessionEvent.TurnFinished(aSession(project = project, state = SessionState.Done))
            }

            FAILED -> {
                SessionEvent.Failed(aSession(project = project, state = SessionState.Failed))
            }

            PULL_REQUEST -> {
                SessionEvent.PullRequestReady(aSession(project = project))
            }

            RUNNING_LONG -> {
                SessionEvent.RunningLong(aSession(project = project), 31.minutes)
            }

            NotificationKind.TERMINAL_BELL -> {
                throw IllegalArgumentException("a bell is not a session event")
            }
        }

    companion object {
        private val PROJECT = ProjectId("2f6c1b9e-6d3a-4f43-9d7b-0a1c5e8b7a11")
        private val OTHER = ProjectId("9b1d7e42-0c55-4c0e-8f3a-5d2e6a4b9c22")
        private val ALL = listOf(POPUP, VOICE, SOUND, DOCK_BADGE)
        private val BADGE = listOf(DOCK_BADGE)
        private val NONE = emptyList<NotificationChannel>()
        private val NIGHT = ctx(localTime = LocalTime.of(23, 0))
        private val QUIET_ON = NotificationSettings(quietHours = QuietHours(enabled = true))

        @JvmStatic
        fun matrixTable(): List<Arguments> =
            listOf(
                arguments(ProjectProfile.DEFAULT, NEEDS_INPUT, ALL),
                arguments(ProjectProfile.DEFAULT, PERMISSION, ALL),
                arguments(ProjectProfile.DEFAULT, TURN_ENDED, listOf(POPUP)),
                arguments(ProjectProfile.DEFAULT, FAILED, ALL),
                arguments(ProjectProfile.DEFAULT, PULL_REQUEST, listOf(POPUP, DOCK_BADGE)),
                arguments(ProjectProfile.DEFAULT, RUNNING_LONG, listOf(VOICE)),
                arguments(ProjectProfile.ERRORS_ONLY, NEEDS_INPUT, NONE),
                arguments(ProjectProfile.ERRORS_ONLY, PERMISSION, NONE),
                arguments(ProjectProfile.ERRORS_ONLY, TURN_ENDED, NONE),
                arguments(ProjectProfile.ERRORS_ONLY, FAILED, ALL),
                arguments(ProjectProfile.ERRORS_ONLY, PULL_REQUEST, NONE),
                arguments(ProjectProfile.ERRORS_ONLY, RUNNING_LONG, NONE),
                arguments(ProjectProfile.MUTED, NEEDS_INPUT, BADGE),
                arguments(ProjectProfile.MUTED, PERMISSION, BADGE),
                arguments(ProjectProfile.MUTED, TURN_ENDED, NONE),
                arguments(ProjectProfile.MUTED, FAILED, BADGE),
                arguments(ProjectProfile.MUTED, PULL_REQUEST, BADGE),
                arguments(ProjectProfile.MUTED, RUNNING_LONG, NONE),
            )

        private fun inProject(
            profile: ProjectProfile,
            base: NotificationSettings,
        ) = base.copy(projects = mapOf(PROJECT to ProjectNotifications(profile)))

        private fun only(
            kind: NotificationKind,
            vararg channels: NotificationChannel,
        ) = NotificationSettings(matrix = mapOf(kind to channels.toSet()))

        @JvmStatic
        @Suppress("LongMethod")
        fun filterTable(): List<Arguments> =
            listOf(
                arguments(
                    "errors only keeps the matrix of a failure",
                    inProject(ProjectProfile.ERRORS_ONLY, only(FAILED, POPUP)),
                    ctx(),
                    FAILED,
                    listOf(POPUP),
                ),
                arguments(
                    "errors only with an empty matrix entry",
                    inProject(ProjectProfile.ERRORS_ONLY, only(FAILED)),
                    ctx(),
                    FAILED,
                    NONE,
                ),
                arguments(
                    "muted needs the badge in the matrix",
                    inProject(ProjectProfile.MUTED, only(FAILED, POPUP, SOUND)),
                    ctx(),
                    FAILED,
                    NONE,
                ),
                arguments(
                    "muted keeps the badge of a custom matrix",
                    inProject(ProjectProfile.MUTED, only(TURN_ENDED, DOCK_BADGE, VOICE)),
                    ctx(),
                    TURN_ENDED,
                    BADGE,
                ),
                arguments(
                    "popup switch is applied before the profile",
                    inProject(ProjectProfile.ERRORS_ONLY, NotificationSettings(popups = false)),
                    ctx(),
                    FAILED,
                    listOf(VOICE, SOUND, DOCK_BADGE),
                ),
                arguments(
                    "voice switch is applied before the profile",
                    inProject(ProjectProfile.ERRORS_ONLY, NotificationSettings(voice = false)),
                    ctx(),
                    FAILED,
                    listOf(POPUP, SOUND, DOCK_BADGE),
                ),
                arguments(
                    "both switches off and muted",
                    inProject(ProjectProfile.MUTED, NotificationSettings(popups = false, voice = false)),
                    ctx(),
                    FAILED,
                    BADGE,
                ),
                arguments(
                    "active app drops the voice of a failure",
                    inProject(ProjectProfile.ERRORS_ONLY, NotificationSettings()),
                    ctx(appActive = true),
                    FAILED,
                    listOf(POPUP, SOUND, DOCK_BADGE),
                ),
                arguments(
                    "quiet hours drop voice and sound of the default profile",
                    inProject(ProjectProfile.DEFAULT, QUIET_ON),
                    NIGHT,
                    NEEDS_INPUT,
                    listOf(POPUP, DOCK_BADGE),
                ),
                arguments(
                    "quiet hours drop voice and sound of a failure",
                    inProject(ProjectProfile.ERRORS_ONLY, QUIET_ON),
                    NIGHT,
                    FAILED,
                    listOf(POPUP, DOCK_BADGE),
                ),
                arguments(
                    "quiet hours leave a muted project its badge",
                    inProject(ProjectProfile.MUTED, QUIET_ON),
                    NIGHT,
                    FAILED,
                    BADGE,
                ),
                arguments(
                    "a Focus leaves a muted project its badge",
                    inProject(ProjectProfile.MUTED, NotificationSettings()),
                    ctx(focusActive = true),
                    FAILED,
                    BADGE,
                ),
                arguments(
                    "errors only stays silent for other kinds at night",
                    inProject(ProjectProfile.ERRORS_ONLY, QUIET_ON),
                    NIGHT,
                    NEEDS_INPUT,
                    NONE,
                ),
            )
    }
}
