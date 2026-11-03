package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.ProjectProfile
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The project profile as a pure filter of channels (FR-N7): the profile only narrows, never adds. */
class ProjectRulesTest {
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("table")
    fun `profile allows the channels of its table`(
        profile: ProjectProfile,
        kind: NotificationKind,
        expected: List<NotificationChannel>,
    ) {
        NotificationChannel.entries.filter { profileAllows(profile, kind, it) } shouldBe expected
    }

    companion object {
        private val ALL = NotificationChannel.entries.toList()
        private val BADGE = listOf(NotificationChannel.DOCK_BADGE)

        @JvmStatic
        fun table(): List<Arguments> =
            NotificationKind.entries.map { arguments(ProjectProfile.DEFAULT, it, ALL) } +
                NotificationKind.entries.map {
                    arguments(ProjectProfile.ERRORS_ONLY, it, if (it == NotificationKind.FAILED) ALL else emptyList())
                } +
                NotificationKind.entries.map { arguments(ProjectProfile.MUTED, it, BADGE) }
    }
}
