package dev.quayside.projects.ui

import dev.quayside.core.domain.ProjectId
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import dev.quayside.sessions.api.pastSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** The question "Znaleziono N starych sesji (podfolder: n, folder główny: m)" (FR-P9): what is counted where. */
class PastSessionCountsTest {
    private val dir = Path.of("/work/plant-puls")
    private val group = aProject(id = "group", dir = dir, subfolders = subfolderNames("docs", "soft"))
    private val plain = aProject(id = "plain", dir = dir)

    private fun disk(
        n: Int,
        cwd: Path,
        project: String = "group",
    ) = pastSession(
        id = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}",
        project = ProjectId(project),
        cwd = cwd,
    )

    @Test
    fun `a project with subfolders counts those in a checked subfolder and those elsewhere`() {
        val sessions =
            listOf(
                disk(1, dir.resolve("soft")),
                disk(2, dir.resolve("soft/api")),
                disk(3, dir.resolve("docs")),
                disk(4, dir),
                disk(5, dir.resolve("marketing")),
            )

        PastSessionCounts.of(group, sessions) shouldBe PastCounts(total = 5, inSubfolders = 3, inRoot = 2)
    }

    @Test
    fun `a project without subfolders has no split`() {
        val sessions = listOf(disk(1, dir.resolve("soft"), "plain"), disk(2, dir, "plain"))

        PastSessionCounts.of(plain, sessions) shouldBe PastCounts(total = 2, inSubfolders = null, inRoot = null)
    }

    @Test
    fun `supervisor sessions and sessions of other projects are not counted`() {
        val sessions =
            listOf(
                disk(1, dir.resolve("soft")),
                disk(2, dir.resolve("soft"), "other"),
                aSession(id = "run00001", project = group.id, cwd = dir.resolve("soft"), state = SessionState.Stopped),
            )

        PastSessionCounts.of(group, sessions) shouldBe PastCounts(total = 1, inSubfolders = 1, inRoot = 0)
    }

    @Test
    fun `no disk sessions give zeros`() {
        PastSessionCounts.of(group, emptyList()) shouldBe PastCounts(total = 0, inSubfolders = 0, inRoot = 0)
        PastSessionCounts.of(plain, emptyList()) shouldBe PastCounts(total = 0, inSubfolders = null, inRoot = null)
    }
}
