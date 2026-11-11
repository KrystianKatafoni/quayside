package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

/** The data types of the manual place of a session (FR-P9, AM-314, ADR 0033). */
class PlaceAssignmentTest {
    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = ["", " ", "\t"])
    fun `a subfolder place rejects a blank name`(name: String) {
        shouldThrow<IllegalArgumentException> { PlaceAssignment.Subfolder(name) }
    }

    @Test
    fun `a subfolder place hides its name in toString`() {
        val text = PlaceAssignment.Subfolder("secret-folder").toString()

        text shouldNotContain "secret"
        text shouldContain "***"
    }

    @Test
    fun `a stored place hides the subfolder name in toString`() {
        val text = StoredPlace(ProjectId("p1"), PlaceAssignment.Subfolder("secret-folder")).toString()

        text shouldNotContain "secret"
        text shouldContain "p1"
    }

    @Test
    fun `place dir is the assigned dir, else the working dir`() {
        val cwd = Path.of("/p/marketing")

        aSession(cwd = cwd).placeDir shouldBe cwd
        aSession(cwd = cwd, assignedDir = Path.of("/p/soft")).placeDir shouldBe Path.of("/p/soft")
    }

    @Test
    fun `a session shows its assigned dir like its working dir`() {
        aSession(assignedDir = Path.of("/p/assigned-here")).toString() shouldContain "assignedDir=/p/assigned-here"
    }

    @Test
    fun `default settings have no places`() {
        SessionHistorySettings().places shouldBe emptyMap()
    }
}
