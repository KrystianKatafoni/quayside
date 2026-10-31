package dev.quayside.commandpalette.api

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CommandMatcherTest {
    private val add = aCommand("projects.add", "Dodaj projekt", CommandGroup.PROJECT)
    private val remove = aCommand("projects.remove", "Usuń projekt", CommandGroup.PROJECT)
    private val rename = aCommand("projects.rename", "Zmień nazwę", CommandGroup.PROJECT, keywords = listOf("rename"))
    private val goToZmora = aCommand("projects.goto.zmora", "Przejdź do projektu: zmora", CommandGroup.GO_TO_PROJECT)
    private val merge = aCommand("sessions.merge", "Łącz sesje", CommandGroup.SESSION)

    private fun titles(
        query: String,
        vararg commands: Command,
    ): List<String> = CommandMatcher.search(query, commands.toList()).map { it.title }

    @ParameterizedTest(name = "[{0}] -> all in entry order")
    @CsvSource("''", "'   '", "'\t'")
    fun `blank query returns everything`(query: String) {
        titles(query, add, remove) shouldBe listOf("Dodaj projekt", "Usuń projekt")
    }

    @Test
    fun `blank query orders by group and then by entry`() {
        titles("", merge, goToZmora, remove, add) shouldBe
            listOf("Usuń projekt", "Dodaj projekt", "Przejdź do projektu: zmora", "Łącz sesje")
    }

    @ParameterizedTest(name = "[{0}] -> {1}")
    @CsvSource(
        "dod, Dodaj projekt",
        "DOD, Dodaj projekt",
        "'  dod  ', Dodaj projekt",
        "usun, Usuń projekt",
        "USUŃ, Usuń projekt",
        "dp, Dodaj projekt",
    )
    fun `query finds a single command`(
        query: String,
        expected: String,
    ) {
        titles(query, add, remove) shouldBe listOf(expected)
    }

    @Test
    fun `word start matches in entry order`() {
        titles("PROJ", add, remove) shouldBe listOf("Dodaj projekt", "Usuń projekt")
    }

    @ParameterizedTest(name = "[{0}] finds Zmień nazwę")
    @CsvSource("nazw", "rename", "RENAME", "nazwe", "zmien")
    fun `query matches title and keywords`(query: String) {
        titles(query, rename) shouldBe listOf("Zmień nazwę")
    }

    @Test
    fun `polish l with stroke matches l`() {
        titles("lacz", merge) shouldBe listOf("Łącz sesje")
        titles("łącz", merge) shouldBe listOf("Łącz sesje")
    }

    @Test
    fun `title prefix beats word start`() {
        titles("zm", goToZmora, rename) shouldBe listOf("Zmień nazwę", "Przejdź do projektu: zmora")
    }

    @Test
    fun `word start beats substring beats subsequence`() {
        val wordStart = aCommand("a", "Open project")
        val substring = aCommand("b", "Reprojection")
        val subsequence = aCommand("c", "Pro job entry")
        titles("proj", subsequence, substring, wordStart) shouldBe
            listOf("Open project", "Reprojection", "Pro job entry")
    }

    @Test
    fun `equal subsequence level keeps entry order`() {
        val substring = aCommand("b", "Reprojection")
        val wordStart = aCommand("a", "Open project")
        titles("pjt", substring, wordStart) shouldBe listOf("Reprojection", "Open project")
    }

    @Test
    fun `equal level is decided by group and then by entry`() {
        val app = aCommand("app", "Preferences", CommandGroup.APP)
        val goTo = aCommand("goto", "Przejdź", CommandGroup.GO_TO_PROJECT)
        val project = aCommand("proj", "Pokaż", CommandGroup.PROJECT)
        val project2 = aCommand("proj2", "Pomoc", CommandGroup.PROJECT)
        titles("p", app, goTo, project2, project) shouldBe listOf("Pomoc", "Pokaż", "Przejdź", "Preferences")
    }

    @Test
    fun `no match returns an empty list`() {
        titles("xyz", add, remove, rename, merge) shouldBe emptyList()
    }

    @Test
    fun `no commands return an empty list`() {
        CommandMatcher.search("dod", emptyList()) shouldBe emptyList()
        CommandMatcher.search("", emptyList()) shouldBe emptyList()
    }
}
