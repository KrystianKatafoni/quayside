package dev.quayside.projects.ui

import dev.quayside.projects.api.DirectoryReport
import dev.quayside.projects.api.GitInfo
import dev.quayside.projects.api.HooksPresence
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

/** Lines of the add dialog (FR-P2, AM-304 plan, table "ReportSummary.lines"). */
internal class ReportSummaryTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("reports")
    fun `each report fact becomes one fixed line`(
        case: String,
        report: DirectoryReport,
        expected: UiText,
    ) {
        withClue(case) { ReportSummary.lines(report) shouldContain expected }
    }

    @Test
    fun `lines come in the order git, CLAUDE md, sessions, hooks`() {
        val lines = ReportSummary.lines(report(git = GitInfo("main"), hasClaudeMd = true, sessionCount = 3))

        lines shouldBe
            listOf(
                UiText("git.branch", listOf("main")),
                UiText("claudeMd.present"),
                UiText("sessions.count", listOf("3")),
                UiText("hooks.none"),
            )
    }

    @Test
    fun `the directory path is never a line argument`() {
        val lines = ReportSummary.lines(report(git = GitInfo("secret-branch")))

        lines shouldHaveSize 4
        lines.flatMap { it.args }.none { it.contains("secret-user") } shouldBe true
    }

    companion object {
        private val DIR: Path = Path.of("/Users/secret-user/projects/agent-manager")

        private fun report(
            git: GitInfo? = null,
            hasClaudeMd: Boolean = false,
            sessionCount: Int? = 0,
            hooks: HooksPresence = HooksPresence.NONE,
        ): DirectoryReport = DirectoryReport(DIR, git, hasClaudeMd, sessionCount, hooks, alreadyAdded = null)

        @JvmStatic
        fun reports(): List<Arguments> =
            listOf(
                arguments("git on a branch", report(git = GitInfo("main")), UiText("git.branch", listOf("main"))),
                arguments("git with a detached HEAD", report(git = GitInfo(null)), UiText("git.detached")),
                arguments("not a git repository", report(git = null), UiText("git.none")),
                arguments("CLAUDE.md present", report(hasClaudeMd = true), UiText("claudeMd.present")),
                arguments("CLAUDE.md absent", report(hasClaudeMd = false), UiText("claudeMd.absent")),
                arguments("no active sessions", report(sessionCount = 0), UiText("sessions.count", listOf("0"))),
                arguments("three active sessions", report(sessionCount = 3), UiText("sessions.count", listOf("3"))),
                arguments("runtime unavailable", report(sessionCount = null), UiText("sessions.unknown")),
                arguments("no hooks", report(hooks = HooksPresence.NONE), UiText("hooks.none")),
                arguments("some hooks", report(hooks = HooksPresence.PARTIAL), UiText("hooks.partial")),
                arguments("all hooks", report(hooks = HooksPresence.INSTALLED), UiText("hooks.installed")),
                arguments("settings unreadable", report(hooks = HooksPresence.UNREADABLE), UiText("hooks.unreadable")),
            )
    }
}
