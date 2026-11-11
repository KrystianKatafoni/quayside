package dev.quayside.missioncontrol.ui

import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.PlaceAssignment
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The list of places of a session in a group (FR-P9, AM-314). The group is `plant-puls` with `bob, Carl, Anna`
 * stored in an order that is neither ASCII nor the group order, so "no sorting" and "ASCII sorting" both fail.
 */
internal class PlaceChoicesTest {
    private val group = OVERVIEW_GROUP

    @Test
    fun `choices are automatic root then subfolders in group order`() {
        val choices = PlaceChoices.of(group)

        choices.map { it.id } shouldBe listOf("auto", "root", "sub.0", "sub.1", "sub.2")
        choices.map { it.assignment } shouldBe
            listOf(
                null,
                PlaceAssignment.ProjectRoot,
                PlaceAssignment.Subfolder("Anna"),
                PlaceAssignment.Subfolder("bob"),
                PlaceAssignment.Subfolder("Carl"),
            )
        choices.map { it.label } shouldBe
            listOf(
                UiText("place.choice.auto"),
                UiText("place.choice.root"),
                UiText("place.choice.subfolder", listOf("Anna")),
                UiText("place.choice.subfolder", listOf("bob")),
                UiText("place.choice.subfolder", listOf("Carl")),
            )
    }

    @Test
    fun `choice ids hold positions not names`() {
        val ids = PlaceChoices.of(group).joinToString { it.id }

        listOf("Anna", "bob", "Carl").forEach { ids shouldNotContain it }
    }

    @Test
    fun `labels read as the plan says, also for a name with a quote and a placeholder`() {
        val odd = aProject(id = "odd", dir = OVERVIEW_DIR, subfolders = listOf(SubfolderName("a'b{0}")))

        PlaceChoices.of(odd).map { MissionControlStrings.format(it.label) } shouldBe
            listOf("Automatycznie", "folder główny", "a'b{0}")
    }

    @Test
    fun `current choice follows assignedDir`() {
        fun current(dir: Path?) = PlaceChoices.current(group, aSession(project = group.id, assignedDir = dir))

        current(null) shouldBe "auto"
        current(OVERVIEW_DIR) shouldBe "root"
        current(OVERVIEW_DIR.resolve("bob")) shouldBe "sub.1"
        current(OVERVIEW_DIR.resolve("Carl")) shouldBe "sub.2"
        current(OVERVIEW_DIR.resolve("Anna")) shouldBe "sub.0"
    }

    @Test
    fun `current choice ignores the working directory`() {
        val session = aSession(project = group.id, cwd = OVERVIEW_DIR.resolve("Carl/api"), assignedDir = null)

        PlaceChoices.current(group, session) shouldBe "auto"
    }

    @Test
    fun `an assigned dir that is no choice any more reads as automatic`() {
        val gone = aSession(project = group.id, assignedDir = OVERVIEW_DIR.resolve("marketing"))

        PlaceChoices.current(group, gone) shouldBe "auto"
    }
}
