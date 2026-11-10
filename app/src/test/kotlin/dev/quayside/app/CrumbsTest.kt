package dev.quayside.app

import dev.quayside.missioncontrol.ui.GlobalView
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** The crumbs of the top bar (AM-816): "Quayside / project", the view instead of the project, the session last. */
class CrumbsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `crumbs of list, global view and terminal mode`(
        @Suppress("UNUSED_PARAMETER") name: String,
        project: String?,
        view: GlobalView?,
        session: String?,
        expected: List<String>,
    ) {
        crumbsOf(project, view, session) shouldBe expected
    }

    companion object {
        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                arguments("nothing selected", null, null, null, listOf("Quayside")),
                arguments("project page", "Agent Manager", null, null, listOf("Quayside", "Agent Manager")),
                arguments("waiting view", null, GlobalView.WAITING, null, listOf("Quayside", "Czeka na mnie")),
                arguments("working view", null, GlobalView.WORKING, null, listOf("Quayside", "Pracuje")),
                arguments("done view", null, GlobalView.DONE, null, listOf("Quayside", "Gotowe")),
                arguments("failed view", null, GlobalView.FAILED, null, listOf("Quayside", "Błędy")),
                arguments(
                    "view wins over a project",
                    "Agent Manager",
                    GlobalView.FAILED,
                    null,
                    listOf("Quayside", "Błędy"),
                ),
                arguments(
                    "terminal mode adds the session",
                    "Agent Manager",
                    null,
                    "fix-login",
                    listOf("Quayside", "Agent Manager", "fix-login"),
                ),
                arguments(
                    "terminal mode in a view",
                    null,
                    GlobalView.WAITING,
                    "fix-login",
                    listOf("Quayside", "Czeka na mnie", "fix-login"),
                ),
                arguments("terminal mode without a project", null, null, "fix-login", listOf("Quayside", "fix-login")),
            )
    }
}
