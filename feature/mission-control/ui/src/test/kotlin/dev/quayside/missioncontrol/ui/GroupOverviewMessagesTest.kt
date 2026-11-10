package dev.quayside.missioncontrol.ui

import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * The texts of the group overview and of the place badge (AM-307, AM-310); the keys are the tests' choice, the Polish
 * texts come from the plan. The counters do not inflect: one format for every number.
 */
internal class GroupOverviewMessagesTest {
    @Test
    fun `section row and counter texts`() {
        mcText("overview.title") shouldBe "Podfoldery"
        mcText("overview.count.waiting", "1") shouldBe "czeka: 1"
        mcText("overview.count.working", "1") shouldBe "pracuje: 1"
        mcText("overview.count.done", "1") shouldBe "gotowe: 1"
        mcText("overview.count.failed", "1") shouldBe "błąd: 1"
    }

    @Test
    fun `the place badge says folder główny`() {
        mcText("row.place.root") shouldBe "folder główny"
        mcText("row.place.subfolder", "soft") shouldBe "folder: soft"
    }

    @Test
    fun `old overview texts are gone`() {
        val old =
            listOf(
                "overview.rest",
                "overview.root",
                "overview.idle",
                "overview.failed",
                "overview.waiting.one",
                "overview.waiting.few",
                "overview.waiting.many",
                "overview.working.one",
                "overview.working.few",
                "overview.working.many",
            )

        old.forEach { MissionControlStrings.keys shouldNotContain it }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("0", "1", "2", "4", "5", "12", "14", "21", "22", "112")
    fun `counters do not inflect`(count: Int) {
        mcText("overview.count.waiting", "$count") shouldBe "czeka: $count"
        mcText("overview.count.working", "$count") shouldBe "pracuje: $count"
        mcText("overview.count.done", "$count") shouldBe "gotowe: $count"
        mcText("overview.count.failed", "$count") shouldBe "błąd: $count"
    }
}
