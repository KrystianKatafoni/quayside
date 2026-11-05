package dev.quayside.notifications.impl

import dev.quayside.notifications.api.NotificationKind
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The fixed mapping notification kind to system sound (AM-812, question 2). */
class SoundRulesTest {
    @ParameterizedTest(name = "{0} plays {1}")
    @MethodSource("table")
    fun `soundFor maps every kind`(
        kind: NotificationKind,
        file: String,
    ) {
        soundFor(kind).fileName shouldBe file
    }

    @Test
    fun `every kind has a sound`() {
        NotificationKind.entries.forEach { soundFor(it) }
        table().map { it.get()[0] }.toSet() shouldBe NotificationKind.entries.toSet()
    }

    @Test
    fun `sound names are the system file names`() {
        SystemSound.entries.map { it.fileName } shouldBe
            listOf("Glass", "Ping", "Basso", "Hero", "Pop", "Submarine", "Tink")
    }

    companion object {
        @JvmStatic
        fun table(): List<Arguments> =
            listOf(
                arguments(NotificationKind.NEEDS_INPUT, "Glass"),
                arguments(NotificationKind.PERMISSION, "Ping"),
                arguments(NotificationKind.FAILED, "Basso"),
                arguments(NotificationKind.TURN_ENDED, "Hero"),
                arguments(NotificationKind.PULL_REQUEST, "Pop"),
                arguments(NotificationKind.RUNNING_LONG, "Submarine"),
                arguments(NotificationKind.TERMINAL_BELL, "Tink"),
            )
    }
}
