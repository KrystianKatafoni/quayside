package dev.quayside.notifications.ui

import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupSettings
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The notification part of the window status bar (AM-816): "głos: wł. · popup: prawy dół". */
internal class NotificationStatusTest {
    @ParameterizedTest(name = "voice {0}, {1}: {2}")
    @MethodSource("cases")
    fun `status names voice and popup position`(
        voice: Boolean,
        position: PopupPosition,
        expected: String,
    ) {
        val settings = NotificationSettings(voice = voice, popup = PopupSettings(position = position))

        notificationStatus(settings) shouldBe expected
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("popupsOff")
    fun `status says popups are off whatever the position`(position: PopupPosition) {
        val settings = NotificationSettings(popups = false, popup = PopupSettings(position = position))

        notificationStatus(settings) shouldBe "głos: wł. · popup: wył."
    }

    companion object {
        private val names =
            mapOf(
                PopupPosition.TOP_LEFT to "lewa góra",
                PopupPosition.TOP_CENTER to "góra",
                PopupPosition.TOP_RIGHT to "prawa góra",
                PopupPosition.BOTTOM_LEFT to "lewy dół",
                PopupPosition.BOTTOM_CENTER to "dół",
                PopupPosition.BOTTOM_RIGHT to "prawy dół",
            )

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(true, false).flatMap { voice ->
                PopupPosition.entries.map { position ->
                    val word = if (voice) "wł." else "wył."
                    arguments(voice, position, "głos: $word · popup: ${names.getValue(position)}")
                }
            }

        @JvmStatic
        fun popupsOff(): List<PopupPosition> = PopupPosition.entries
    }
}
