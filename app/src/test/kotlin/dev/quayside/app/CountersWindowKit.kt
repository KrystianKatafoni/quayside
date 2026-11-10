package dev.quayside.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.FakeSessionHistorySettings
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldPastSession
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import java.nio.file.Files
import java.nio.file.Path

internal const val COUNTERS_WAIT_MS = 5_000L

/**
 * The group of the AM-310 window tests on fakes: `plant-puls` with the subfolders `docs` and `soft`, and a plain
 * project `other`. Directories are created in [root]; sessions are named after where they run.
 */
internal class CountersWorld(
    root: Path,
) {
    val plantDir: Path = Files.createDirectory(root.resolve("plant-puls"))
    val plant =
        aProject(
            id = "project0001",
            dir = plantDir,
            displayName = "plant-puls",
            subfolders = subfolderNames("docs", "soft"),
        )
    val other = aProject(id = "project0002", dir = Files.createDirectory(root.resolve("other")), displayName = "other")
    val projects = FakeProjectsApi(initial = listOf(plant, other))

    /** A session of the group in [cwd] (relative to the project directory), started at T0 + [index] seconds. */
    fun session(
        id: String,
        cwd: String,
        state: SessionState,
        index: Long,
    ) = WorldSession(SessionId(id), plantDir.resolve(cwd), state, id, T0.plusSeconds(index))

    fun fake(
        vararg sessions: WorldSession,
        past: List<WorldPastSession> = emptyList(),
        settings: FakeSessionHistorySettings = FakeSessionHistorySettings(),
    ): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects =
                    listOf(
                        WorldProject(plant.id, plant.dir, subfolders = listOf("docs", "soft")),
                        WorldProject(other.id, other.dir),
                    ),
                sessions = sessions.toList(),
                pastSessions = past,
            ),
            historySettings = settings,
        )
}

/** The counters announced under the node tagged [tag] as `kind -> number`, read from the unmerged tree. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.counters(tag: String): Map<String, Int> =
    onAllNodes(
        hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
        useUnmergedTree = true,
    ).fetchSemanticsNodes()
        .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        .mapNotNull { text -> COUNTER_TEXT.matchEntire(text)?.destructured?.let { (kind, n) -> kind to n.toInt() } }
        .toMap()

private val COUNTER_TEXT = Regex("(czeka|pracuje|gotowe|błąd): (\\d+)")

/**
 * Waits until the row [tag] (a sidebar row or a row of the group overview) has exactly the chips of the non-zero
 * counts (a count of 0 means no chip; [done] is only for rows of the group overview, the sidebar has no "done" chip).
 * Pass a row that has at least one chip when the aim is to prove a row empty after loading: with all four at 0 this
 * returns at once.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitChips(
    tag: String,
    waiting: Int = 0,
    working: Int = 0,
    failed: Int = 0,
    done: Int = 0,
) {
    val expected =
        mapOf("czeka" to waiting, "pracuje" to working, "gotowe" to done, "błąd" to failed).filterValues { it != 0 }
    waitUntil(timeoutMillis = COUNTERS_WAIT_MS) { counters(tag) == expected }
}

/** True when a node under [tag] shows exactly [text] (unmerged tree: texts cleared for accessibility count too). */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.shows(
    tag: String,
    text: String,
): Boolean =
    onAllNodes(hasText(text) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()

/** True when a node under [tag] (or the node itself) is read as [description]. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.reads(
    tag: String,
    description: String,
): Boolean =
    onAllNodes(
        hasContentDescription(description) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().isNotEmpty()

/** Number of nodes whose tag starts with [prefix]. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.tagged(prefix: String): Int =
    onAllNodes(
        SemanticsMatcher("tag starts with $prefix") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        },
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size
