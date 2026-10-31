package dev.quayside.projects.ui

import dev.quayside.projects.api.DirectoryReport
import dev.quayside.projects.api.GitInfo
import dev.quayside.projects.api.HooksPresence

/** Lines of the add dialog, in order: git, CLAUDE.md, sessions, hooks. The path is shown apart, never here. */
internal object ReportSummary {
    fun lines(report: DirectoryReport): List<UiText> =
        listOf(
            git(report.git),
            UiText(if (report.hasClaudeMd) "claudeMd.present" else "claudeMd.absent"),
            report.sessionCount?.let { UiText("sessions.count", listOf(it.toString())) } ?: UiText("sessions.unknown"),
            hooks(report.hooks),
        )

    private fun git(git: GitInfo?): UiText {
        val branch = git?.branch
        return when {
            git == null -> UiText("git.none")
            branch == null -> UiText("git.detached")
            else -> UiText("git.branch", listOf(branch))
        }
    }

    private fun hooks(hooks: HooksPresence): UiText =
        when (hooks) {
            HooksPresence.NONE -> UiText("hooks.none")
            HooksPresence.PARTIAL -> UiText("hooks.partial")
            HooksPresence.INSTALLED -> UiText("hooks.installed")
            HooksPresence.UNREADABLE -> UiText("hooks.unreadable")
        }
}
