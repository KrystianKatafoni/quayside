package dev.quayside.projects.ui

import dev.quayside.projects.api.DirectoryReport
import dev.quayside.projects.api.GitInfo
import dev.quayside.projects.api.HooksPresence
import dev.quayside.projects.api.StorageIssue
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/** Texts of `i18n/projects.properties` (NFR-10): every key the sidebar can show exists and is filled in. */
class ProjectsStringsTest {
    @Test
    fun `every message key has a text`() {
        ProjectsStrings.keys shouldContainAll allKeys()
        allKeys().forEach { key -> withClue(key) { ProjectsStrings[key].shouldNotBeBlank() } }
    }

    @Test
    fun `the obsolete listing text is gone`() {
        ProjectsStrings.keys.contains("remove.checking") shouldBe false
    }

    @Test
    fun `arguments are filled into the text`() {
        argumentTexts().forEach { text ->
            withClue(text) { text.args.forEach { ProjectsStrings.format(text) shouldContain it } }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buttons")
    fun `dialog buttons say what the plan says`(
        key: String,
        expected: String,
    ) {
        ProjectsStrings[key] shouldBe expected
    }

    companion object {
        /** Fixed texts of the composables and of the controller (not from [ProjectMessages] or [ReportSummary]). */
        val UI_KEYS: Set<String> =
            setOf(
                "sidebar.title",
                "sidebar.empty",
                "sidebar.add",
                "row.remove",
                "row.rename",
                "row.waiting",
                "row.working",
                "banner.dismiss",
                "add.title",
                "add.inspecting",
                "add.confirm",
                "remove.title",
                "rename.title",
                "rename.save",
                "remove.activeSessions",
                "remove.sessionsUnknown",
                "remove.confirm",
                "remove.stopAndRemove",
                "remove.keepSessions",
                "remove.removeFromList",
                "remove.stopFailed",
                "remove.runtimeFailed",
                "dialog.busy",
                "dialog.cancel",
                "dialog.close",
            )

        private fun reports(): List<DirectoryReport> {
            val dir = Path.of("/Users/test/projects/agent-manager")
            val gits = listOf(GitInfo("main"), GitInfo(null), null)
            return gits.flatMap { git ->
                listOf(true, false).flatMap { claudeMd ->
                    listOf(0, 3, null).flatMap { sessions ->
                        HooksPresence.entries.map { hooks ->
                            DirectoryReport(dir, git, claudeMd, sessions, hooks, null)
                        }
                    }
                }
            }
        }

        private fun generated(): List<UiText> =
            ProjectMessagesTest.allErrors().map(ProjectMessages::of) +
                StorageIssue.entries.map(ProjectMessages::of) +
                reports().flatMap(ReportSummary::lines)

        private fun argumentTexts(): List<UiText> =
            generated().filter { it.args.isNotEmpty() } +
                listOf(
                    UiText("remove.activeSessions", listOf("3")),
                    UiText("row.waiting", listOf("2")),
                    UiText("row.working", listOf("5")),
                )

        fun allKeys(): Set<String> = generated().map { it.key }.toSet() + UI_KEYS

        @JvmStatic
        fun buttons(): List<Arguments> =
            listOf(
                arguments("sidebar.add", "Dodaj projekt"),
                arguments("row.rename", "Zmień nazwę…"),
                arguments("rename.save", "Zapisz"),
                arguments("row.waiting", "czeka: {0}"),
                arguments("row.working", "pracuje: {0}"),
                arguments("add.inspecting", "Sprawdzanie…"),
                arguments("add.confirm", "Dodaj"),
                arguments("remove.stopAndRemove", "Zatrzymaj sesje i usuń"),
                arguments("remove.keepSessions", "Usuń z listy, sesje działają dalej"),
                arguments("remove.removeFromList", "Usuń z listy"),
                arguments("remove.sessionsUnknown", "Stan sesji nie jest znany"),
                arguments("dialog.cancel", "Anuluj"),
            )
    }
}
