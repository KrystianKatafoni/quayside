package dev.quayside.notifications.ui

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettingsError
import dev.quayside.notifications.api.PopupDuration
import dev.quayside.notifications.api.PopupPosition
import dev.quayside.notifications.api.PopupSettings
import dev.quayside.notifications.api.PopupStyle
import dev.quayside.notifications.api.SpeechSettings
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

/** Texts of `i18n/notification-settings.properties` (NFR-6, NFR-10). */
class SettingsStringsTest {
    @Test
    fun `every kind and channel has a label`() {
        NotificationKind.entries.forEach { kind ->
            withClue(kind.name) { SettingsStrings.of(kind).shouldNotBeBlank() }
        }
        NotificationChannel.entries.forEach { channel ->
            withClue(channel.name) { SettingsStrings.of(channel).shouldNotBeBlank() }
        }
        NotificationKind.entries
            .map(SettingsStrings::of)
            .distinct()
            .size shouldBe NotificationKind.entries.size
        NotificationChannel.entries
            .map(SettingsStrings::of)
            .distinct()
            .size shouldBe NotificationChannel.entries.size
    }

    @Test
    fun `checkbox label names the kind and the channel`() {
        NotificationKind.entries.forEach { kind ->
            NotificationChannel.entries.forEach { channel ->
                val label = SettingsStrings.cellLabel(kind, channel)

                label shouldContain SettingsStrings.of(kind)
                label shouldContain " – "
                label shouldContain SettingsStrings.of(channel)
            }
        }
    }

    @Test
    fun `the threshold message names the allowed range`() {
        val message = SettingsStrings.of(InvalidField(NotificationField.RUNNING_LONG_MINUTES))

        message shouldContain "od 1 do 1440"
    }

    @Test
    fun `every popup option has its own label`() {
        fun labels(
            prefix: String,
            values: List<Enum<*>>,
        ): List<String> = values.map { SettingsStrings.option(prefix, it) }

        val position = labels("position", PopupPosition.entries)
        val duration = labels("duration", PopupDuration.entries)
        val style = labels("style", PopupStyle.entries)

        (position + duration + style).forEach { it.shouldNotBeBlank() }
        position.distinct().size shouldBe 6
        duration.distinct().size shouldBe 3
        style.distinct().size shouldBe 2
    }

    @Test
    fun `the max visible message names the allowed range`() {
        val message = SettingsStrings.of(InvalidField(NotificationField.MAX_VISIBLE))

        message shouldContain "od 1 do ${PopupSettings.MAX_VISIBLE_LIMIT}"
    }

    @Test
    fun `every refused field has its own message`() {
        val messages =
            NotificationField.entries.map { field ->
                SettingsStrings.of(
                    InvalidField(
                        field,
                        NotificationKind.FAILED.takeIf {
                            field ==
                                NotificationField.TEMPLATE
                        },
                    ),
                )
            }

        messages.forEach { it.shouldNotBeBlank() }
        messages.distinct().size shouldBe NotificationField.entries.size
        SettingsStrings.keys.contains("invalid.other") shouldBe false
    }

    @Test
    fun `the rate and volume messages name the allowed range`() {
        val rate = SettingsStrings.of(InvalidField(NotificationField.RATE))
        val volume = SettingsStrings.of(InvalidField(NotificationField.VOLUME))

        rate shouldContain "od ${SpeechSettings.MIN_RATE} do ${SpeechSettings.MAX_RATE}"
        volume shouldContain "liczba od ${SpeechSettings.MIN_VOLUME} do ${SpeechSettings.MAX_VOLUME} (%)"
    }

    @Test
    fun `the template message names the length limit`() {
        val message = SettingsStrings.of(InvalidField(NotificationField.TEMPLATE, NotificationKind.FAILED))

        message shouldStartWith "Komunikat:"
        message shouldContain "najwyżej ${SpeechSettings.MAX_TEMPLATE_LENGTH} znaków"
        message shouldContain "[["
        message shouldContain "]]"
    }

    @Test
    fun `each write error has its own message`() {
        val messages = NotificationSettingsError.entries.map(SettingsStrings::of)

        messages.forEach { it.shouldNotBeBlank() }
        messages.distinct().size shouldBe NotificationSettingsError.entries.size
        SettingsStrings.of(NotificationSettingsError.NEWER_SCHEMA) shouldNotBe
            SettingsStrings.of(NotificationSettingsError.WRITE_FAILED)
    }

    @Test
    fun `every text key is not blank`() {
        SettingsStrings.keys.forEach { SettingsStrings[it].shouldNotBeBlank() }
    }
}
