package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** What the focus of the sidebar does to the selection when a project is a group (FR-P7, AM-306). */
internal class SelectionFocusTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `focus from the sidebar updates the selection`(
        @Suppress("UNUSED_PARAMETER") name: String,
        before: Selection,
        project: String?,
        subfolder: String?,
        expected: Selection,
    ) {
        val focus = project?.let { ProjectFocus(ProjectId(it), subfolder?.let(::SubfolderName)) }

        before.onFocus(focus) shouldBe expected
    }

    companion object {
        // Ids and names are passed as text: JUnit cannot pass a nullable value class.
        private val mine = ProjectId("mine")
        private val other = ProjectId("other")
        private val soft = SubfolderName("soft")
        private val docs = SubfolderName("docs")
        private val picked = SessionId("picked")

        private val onProject = Selection(mine, filter = SessionFilter.ACTIVE, session = picked)
        private val onSoft = Selection(mine, filter = SessionFilter.FINISHED, session = picked, subfolder = soft)

        @JvmStatic
        fun cases(): List<Arguments> = subfolderCases() + projectCases()

        private fun subfolderCases(): List<Arguments> =
            listOf(
                arguments("the same project and subfolder keep everything", onSoft, "mine", "soft", onSoft),
                arguments(
                    "another subfolder starts a fresh page",
                    onSoft,
                    "mine",
                    "docs",
                    Selection(mine, subfolder = docs),
                ),
                arguments(
                    "a subfolder after the project starts a fresh page",
                    onProject,
                    "mine",
                    "soft",
                    Selection(mine, subfolder = soft),
                ),
                arguments("the project after a subfolder starts a fresh page", onSoft, "mine", null, Selection(mine)),
                arguments(
                    "a subfolder of another project",
                    onSoft,
                    "other",
                    "soft",
                    Selection(other, subfolder = soft),
                ),
            )

        private fun projectCases(): List<Arguments> =
            listOf(
                arguments(
                    "a subfolder leaves the global view",
                    Selection(mine, view = GlobalView.WAITING, session = picked),
                    "mine",
                    "soft",
                    Selection(mine, subfolder = soft),
                ),
                arguments("the same project keeps everything", onProject, "mine", null, onProject),
                arguments("another project drops the subfolder", onSoft, "other", null, Selection(other)),
                arguments("no focus on a subfolder page clears everything", onSoft, null, null, Selection()),
                arguments(
                    "no focus keeps the global view and drops the subfolder",
                    onSoft.copy(view = GlobalView.WORKING),
                    null,
                    null,
                    onSoft.copy(project = null, subfolder = null, view = GlobalView.WORKING),
                ),
            )
    }
}
