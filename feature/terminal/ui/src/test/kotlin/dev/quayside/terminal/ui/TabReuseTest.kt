package dev.quayside.terminal.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource

/** Which tab a keyed request reuses (plan AM-510, table 1). */
internal class TabReuseTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `reuse of a tab by key`(
        @Suppress("UNUSED_PARAMETER") name: String,
        tabs: List<TerminalTab>,
        expected: TabReuse,
    ) {
        TerminalTabs.reuseOf(tabs, KEY) shouldBe expected
    }

    companion object {
        private val KEY = TabKey("attach:s1")

        private fun keyed(
            id: Int,
            exitCode: Int? = null,
            project: TerminalProject = ALPHA,
        ): TerminalTab = aTab(id, project = project, exitCode = exitCode).keyed(KEY.value)

        @JvmStatic
        fun cases(): List<Arguments> =
            listOf(
                arguments("no tabs", emptyList<TerminalTab>(), TabReuse.Open),
                arguments("only tabs without a key", listOf(aTab(1), aTab(2)), TabReuse.Open),
                arguments("only a tab of another key", listOf(aTab(1).keyed("attach:s2")), TabReuse.Open),
                arguments("a live tab of the key", listOf(aTab(1), keyed(2)), TabReuse.Focus(tabId(2))),
                arguments(
                    "a live tab of the key in another group",
                    listOf(keyed(1, project = BETA)),
                    TabReuse.Focus(tabId(1)),
                ),
                arguments(
                    "only an exited tab of the key",
                    listOf(aTab(1), keyed(2, exitCode = 0)),
                    TabReuse.Replace(tabId(2)),
                ),
                arguments(
                    "an exited and a live tab of the key",
                    listOf(keyed(1, exitCode = 1), keyed(2)),
                    TabReuse.Focus(tabId(2)),
                ),
                arguments(
                    "a live and an exited tab of the key",
                    listOf(keyed(1), keyed(2, exitCode = 1)),
                    TabReuse.Focus(tabId(1)),
                ),
            )
    }
}
