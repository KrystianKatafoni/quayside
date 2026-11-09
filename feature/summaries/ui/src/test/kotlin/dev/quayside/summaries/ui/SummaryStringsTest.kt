package dev.quayside.summaries.ui

import dev.quayside.summaries.api.SummarySettingsError
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test

/** Texts of `i18n/summary-settings.properties` (NFR-10). */
class SummaryStringsTest {
    @Test
    fun `every key the dialog reads is present and not blank`() {
        val used = listOf("title", "info", "useModel", "costNote", "restartNote", "save", "cancel")

        SummaryStrings.keys shouldContainAll used
        used.forEach { withClue(it) { SummaryStrings[it].shouldNotBeBlank() } }
    }

    @Test
    fun `every error has a message`() {
        SummarySettingsError.entries.forEach { withClue(it.name) { SummaryStrings.of(it).shouldNotBeBlank() } }
    }
}
