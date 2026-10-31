package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** What the project stream of the sidebar does to the selection (AM-407 plan, decision 1, table 2). */
internal class SelectionTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `project from the sidebar updates the selection`(
        @Suppress("UNUSED_PARAMETER") name: String,
        before: Selection,
        id: String?,
        expected: Selection,
    ) {
        before.onProject(id?.let(::ProjectId)) shouldBe expected
    }

    companion object {
        // The ids are passed as text: JUnit cannot pass a nullable value class.
        private const val MINE = "mine"
        private const val OTHER = "other"
        private val mine = ProjectId(MINE)
        private val other = ProjectId(OTHER)
        private val picked = SessionId("picked")

        private val onPage = Selection(mine, filter = SessionFilter.ACTIVE, session = picked)
        private val inView = Selection(project = null, view = GlobalView.WAITING, session = picked)

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                arguments("the same project keeps everything", onPage, MINE, onPage),
                arguments(
                    "the same project keeps a view",
                    onPage.copy(view = GlobalView.FAILED),
                    MINE,
                    onPage.copy(view = GlobalView.FAILED),
                ),
                arguments("another project starts with the whole list", onPage, OTHER, Selection(project = other)),
                arguments("another project leaves the global view", inView, OTHER, Selection(project = other)),
                arguments(
                    "another project over a view and project",
                    onPage.copy(view = GlobalView.WORKING),
                    OTHER,
                    Selection(project = other),
                ),
                arguments(
                    "no project keeps the global view",
                    onPage.copy(view = GlobalView.WORKING),
                    null,
                    onPage.copy(project = null, view = GlobalView.WORKING),
                ),
                arguments("no project in a view changes nothing", inView, null, inView),
                arguments("no project on the page clears everything", onPage, null, Selection()),
                arguments("no project, nothing selected", Selection(), null, Selection()),
            )
    }
}
