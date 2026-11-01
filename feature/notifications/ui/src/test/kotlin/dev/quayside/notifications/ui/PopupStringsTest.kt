package dev.quayside.notifications.ui

import dev.quayside.notifications.api.NotificationKind
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test

/** Texts of `i18n/popup.properties` (NFR-10). */
class PopupStringsTest {
    @Test
    fun `every kind has a title`() {
        NotificationKind.entries.forEach { kind -> withClue(kind.name) { PopupStrings.title(kind).shouldNotBeBlank() } }
    }

    @Test
    fun `buttons have texts`() {
        PopupStrings["popup.attach"] shouldBe "Otwórz"
        PopupStrings["popup.mute"] shouldBe "Wycisz 15 min"
        PopupStrings["popup.dismiss"] shouldBe "Zamknij"
    }
}
