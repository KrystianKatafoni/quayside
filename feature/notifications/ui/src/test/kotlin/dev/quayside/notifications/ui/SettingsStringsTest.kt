package dev.quayside.notifications.ui

import dev.quayside.notifications.api.NotificationChannel
import dev.quayside.notifications.api.NotificationKind
import dev.quayside.notifications.api.NotificationSettingsError
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
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

        message shouldContain "1"
        message shouldContain "1440"
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
