package dev.quayside.notifications.ui

import dev.quayside.notifications.api.ProjectProfile
import dev.quayside.notifications.api.VoiceId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test

/** Texts of the quiet hours and project exception sections (FR-N6, FR-N7, NFR-10, plan AM-706). */
class SettingsStringsQuietTest {
    @Test
    fun `quiet messages name the allowed range`() {
        // the whole fragment: "1" would match inside "10"
        SettingsStrings.of(InvalidField(NotificationField.QUIET_FROM)) shouldContain "od 00:00 do 23:59"
        SettingsStrings.of(InvalidField(NotificationField.QUIET_TO)) shouldContain "od 00:00 do 23:59"
    }

    @Test
    fun `quiet messages name the field they belong to`() {
        SettingsStrings.of(InvalidField(NotificationField.QUIET_FROM)) shouldStartWith "Początek ciszy"
        SettingsStrings.of(InvalidField(NotificationField.QUIET_TO)) shouldStartWith "Koniec ciszy"
    }

    @Test
    fun `every profile has its own label`() {
        ProjectProfile.entries.map(SettingsStrings::profile) shouldBe listOf("Domyślny", "Tylko błędy", "Wyciszony")
    }

    @Test
    fun `project voice null is the global voice`() {
        SettingsStrings.projectVoice(VoiceChoice(id = null, locale = null, missing = false)) shouldBe "Głos globalny"
    }

    @Test
    fun `a project voice that is not installed is marked like the global voice list`() {
        val choice = VoiceChoice(VoiceId("Anna"), locale = null, missing = true)

        SettingsStrings.projectVoice(choice) shouldBe "Anna (niezainstalowany)"
    }

    @Test
    fun `the focus note says it does not work yet`() {
        SettingsStrings["settings.focusNote"] shouldStartWith "Jeszcze nie działa"
    }

    @Test
    fun `label templates of the project choices hold the name placeholder`() {
        // the labels themselves are checked on the screen: `choices are named with the project for the screen reader`
        SettingsStrings["settings.projectProfile"] shouldContain "{name}"
        SettingsStrings["settings.projectVoice"] shouldContain "{name}"
    }
}
