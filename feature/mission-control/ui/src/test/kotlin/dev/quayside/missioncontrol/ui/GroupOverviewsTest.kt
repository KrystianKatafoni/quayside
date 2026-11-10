package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/** One row of the table: the sessions of the group and what the overview shows (rows in display order). */
internal data class OverviewCase(
    val title: String,
    val sessions: List<Session>,
    val rows: List<Pair<String, Counts>>,
) {
    override fun toString(): String = title
}

/**
 * The pure overview of a group (FR-P8, AM-307, AM-310): counters per subfolder, tiers waiting > failed > working > the
 * rest, [dev.quayside.projects.api.ProjectGroups.order] inside a tier. Sessions outside every subfolder have no row;
 * `done` is counted but does not lift a row's tier.
 */
internal class GroupOverviewsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `overview of the group`(case: OverviewCase) {
        val overview = GroupOverviews.of(OVERVIEW_GROUP, case.sessions).shouldNotBeNull()

        overview.project shouldBe OVERVIEW_GROUP.id
        overview.rows.map { it.name.value to it.stats.counts() } shouldBe case.rows
    }

    @Test
    fun `sessions outside the subfolders make no row and leave the subfolder counters alone`() {
        val inFolders = listOf(works("a", "bob"), waits("b", "Anna"))
        val outside =
            listOf(
                works("c", ""),
                fails("d", "marketing"),
                waits("e", "bobx"),
                groupSession("f", "", SessionState.Done),
            )

        val without = GroupOverviews.of(OVERVIEW_GROUP, inFolders).shouldNotBeNull()
        val with = GroupOverviews.of(OVERVIEW_GROUP, inFolders + outside).shouldNotBeNull()

        with.rows.map { it.name.value } shouldBe listOf("Anna", "bob", "Carl")
        with.rows.map { it.name.value to it.stats.counts() } shouldBe
            without.rows.map { it.name.value to it.stats.counts() }
        with.rows.map { it.stats.counts() } shouldBe
            listOf(Counts(waiting = 1), Counts(working = 1), Counts())
    }

    @Test
    fun `project without subfolders has no overview`() {
        val plain = aProject(id = "plain", dir = OVERVIEW_DIR, displayName = "plain")

        GroupOverviews.of(plain, listOf(groupSession("a", "", SessionState.Working))).shouldBeNull()
    }

    companion object {
        private val zero = Counts()
        private val sorted = listOf("Anna", "bob", "Carl")

        private fun zeros(vararg names: String): List<Pair<String, Counts>> = names.map { it to zero }

        private fun case(
            title: String,
            sessions: List<Session>,
            rows: List<Pair<String, Counts>>,
        ) = Arguments.of(OverviewCase(title, sessions, rows))

        private fun waits(
            id: String,
            cwd: String,
        ) = groupSession(id, cwd, SessionState.AwaitingInput("q"))

        private fun fails(
            id: String,
            cwd: String,
        ) = groupSession(id, cwd, SessionState.Failed)

        private fun works(
            id: String,
            cwd: String,
        ) = groupSession(id, cwd, SessionState.Working)

        @JvmStatic
        fun cases(): Stream<Arguments> = Stream.concat(orderCases().stream(), scopeCases().stream())

        /** Order: no sessions (ignoring case, not ASCII) and the tiers. */
        @Suppress("LongMethod")
        private fun orderCases(): List<Arguments> =
            listOf(
                case("no sessions: all rows at zero, sorted ignoring case", emptyList(), zeros(*sorted.toTypedArray())),
                case(
                    "row order by tiers is unchanged: done does not lift a row",
                    listOf(
                        groupSession("a", "Anna", SessionState.Done),
                        groupSession("b", "Anna", SessionState.Done),
                        works("c", "Carl"),
                    ),
                    listOf("Carl" to Counts(working = 1), "Anna" to Counts(done = 2), "bob" to zero),
                ),
                case(
                    "waiting first, then failed, then the rest",
                    listOf(waits("a", "Carl"), fails("b", "bob")),
                    listOf("Carl" to Counts(waiting = 1), "bob" to Counts(failed = 1), "Anna" to zero),
                ),
                case(
                    "done is counted per row",
                    listOf(
                        groupSession("a", "bob", SessionState.Done),
                        groupSession("b", "bob/x", SessionState.Done),
                        groupSession("c", "bob", SessionState.Failed),
                        groupSession("d", "Carl", SessionState.Done),
                        groupSession("e", "Carl", SessionState.Stopped),
                    ),
                    listOf("bob" to Counts(done = 2, failed = 1), "Anna" to zero, "Carl" to Counts(done = 1)),
                ),
                case(
                    "the tier decides, not the number",
                    listOf(waits("a", "bob"), waits("b", "bob"), waits("c", "bob"), waits("d", "Anna")),
                    listOf("Anna" to Counts(waiting = 1), "bob" to Counts(waiting = 3), "Carl" to zero),
                ),
                case(
                    "failed before working",
                    listOf(works("a", "Anna"), fails("b", "Carl")),
                    listOf("Carl" to Counts(failed = 1), "Anna" to Counts(working = 1), "bob" to zero),
                ),
                case(
                    "a row that waits and fails is in the waiting tier",
                    listOf(waits("a", "Carl"), fails("b", "Carl"), fails("c", "bob"), works("d", "Anna")),
                    listOf(
                        "Carl" to Counts(waiting = 1, failed = 1),
                        "bob" to Counts(failed = 1),
                        "Anna" to Counts(working = 1),
                    ),
                ),
                case(
                    "a row that fails and works is in the failed tier",
                    listOf(works("a", "Anna"), works("b", "bob"), fails("c", "bob")),
                    listOf("bob" to Counts(failed = 1, working = 1), "Anna" to Counts(working = 1), "Carl" to zero),
                ),
            )

        /** Scope: the group directory, a sibling name, a nested project, another project, states, deep paths. */
        @Suppress("LongMethod")
        private fun scopeCases(): List<Arguments> =
            listOf(
                case(
                    "the root and unchecked subdirectories are not counted",
                    listOf(waits("a", ""), works("b", "marketing")),
                    zeros(*sorted.toTypedArray()),
                ),
                case(
                    "a failed session outside the subfolders is not counted",
                    listOf(fails("a", "marketing")),
                    zeros(*sorted.toTypedArray()),
                ),
                case(
                    "a folder named like a subfolder prefix is not counted",
                    listOf(waits("a", "bobx")),
                    zeros(*sorted.toTypedArray()),
                ),
                case(
                    "done and stopped outside the subfolders are not counted",
                    listOf(
                        groupSession("a", "bob", SessionState.Stopped),
                        groupSession("b", "", SessionState.Done),
                        groupSession("c", "", SessionState.Stopped),
                        groupSession("d", "marketing", SessionState.Done),
                    ),
                    zeros(*sorted.toTypedArray()),
                ),
                case(
                    "a session of a nested project is not counted",
                    listOf(groupSession("a", "bob/x", SessionState.Working, project = OVERVIEW_NESTED.id)),
                    zeros(*sorted.toTypedArray()),
                ),
                case(
                    "a session of another project is not counted",
                    listOf(
                        groupSession("a", "Anna", SessionState.Working, project = ProjectId("other")),
                        groupSession("b", "Anna/api", SessionState.Working, project = null),
                    ),
                    zeros(*sorted.toTypedArray()),
                ),
                case(
                    "both ways of waiting count, also deeper in the folder",
                    listOf(waits("a", "Anna"), groupSession("b", "Anna/api", SessionState.AwaitingPermission("Bash"))),
                    listOf("Anna" to Counts(waiting = 2), "bob" to zero, "Carl" to zero),
                ),
            )
    }
}
