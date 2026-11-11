package dev.quayside.missioncontrol.ui

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The texts of the manual place (FR-P9, AM-314): they exist, read as the plan says and keep the name as it is. */
internal class PlaceStringsTest {
    @Test
    fun `the texts of the manual place exist`() {
        MissionControlStrings.keys shouldContainAll
            listOf(
                "action.assign_place",
                "action.assign_place.busy",
                "action.assign_place.failed",
                "place.dialog.title",
                "place.choice.auto",
                "place.choice.root",
                "place.choice.subfolder",
                "details.place.manual.subfolder",
                "details.place.manual.root",
            )
    }

    @Test
    fun `the texts read as the plan says`() {
        MissionControlStrings["action.assign_place"] shouldBe "Przypisz do podfolderu…"
        MissionControlStrings["place.choice.auto"] shouldBe "Automatycznie"
        MissionControlStrings["place.choice.root"] shouldBe "folder główny"
        MissionControlStrings.format(UiText("details.place.manual.root")) shouldBe "Miejsce: folder główny (ręcznie)"
    }

    @Test
    fun `the manual mark takes the name as it is`() {
        val text = MissionControlStrings.format(UiText("details.place.manual.subfolder", listOf("a'b{0}")))

        text shouldBe "Miejsce: a'b{0} (ręcznie)"
    }
}
