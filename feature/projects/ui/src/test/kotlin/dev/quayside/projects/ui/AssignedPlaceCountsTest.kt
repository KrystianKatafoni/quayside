package dev.quayside.projects.ui

import dev.quayside.core.testing.T0
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.Session
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsSnapshot
import dev.quayside.sessions.api.SnapshotStatus
import dev.quayside.sessions.api.aSession
import dev.quayside.sessions.api.pastSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Where a session counts in the sidebar and in the question about old sessions follows `Session.placeDir`, the place
 * the user chose (FR-P9, AM-314), not the working directory. Every session here runs in one subfolder and was put in
 * another place, so a count that still reads `cwd` gives another answer.
 */
internal class AssignedPlaceCountsTest {
    private val dir = Path.of("/p/plant-puls")
    private val plant = aProject(id = "plant", dir = dir, subfolders = subfolderNames("docs", "soft"))
    private val docs = ProjectFocus(plant.id, SubfolderName("docs"))
    private val soft = ProjectFocus(plant.id, SubfolderName("soft"))

    private fun view(vararg sessions: Session) =
        SessionsView.of(listOf(plant), SessionsSnapshot(sessions.toList(), SnapshotStatus.Fresh(T0)))

    @Test
    fun `sidebar subfolder counts use the assigned place`() {
        val view =
            view(
                aSession(
                    "in-soft-put-in-docs",
                    plant.id,
                    cwd = dir.resolve("soft/api"),
                    assignedDir = dir.resolve("docs"),
                ),
                aSession(
                    "in-docs-put-in-root",
                    plant.id,
                    SessionState.AwaitingInput("q"),
                    cwd = dir.resolve("docs"),
                    assignedDir = dir,
                ),
                aSession(
                    "in-root-put-in-soft",
                    plant.id,
                    SessionState.Failed,
                    cwd = dir,
                    assignedDir = dir.resolve("soft"),
                ),
            )

        view.at(docs) shouldBe StatusCounts(working = 1)
        view.at(soft) shouldBe StatusCounts(failed = 1)
    }

    @Test
    fun `sidebar main row still sums the whole group`() {
        val view =
            view(
                aSession("a", plant.id, cwd = dir.resolve("soft"), assignedDir = dir.resolve("docs")),
                aSession("b", plant.id, SessionState.AwaitingInput("q"), cwd = dir.resolve("docs"), assignedDir = dir),
            )

        view.at(ProjectFocus(plant.id)) shouldBe StatusCounts(waiting = 1, working = 1)
    }

    @Test
    fun `past sessions counts use the assigned place`() {
        val past =
            listOf(
                pastSession("00000000-0000-4000-8000-000000000001", plant.id, dir.resolve("soft"), assignedDir = dir),
                pastSession(
                    "00000000-0000-4000-8000-000000000002",
                    plant.id,
                    dir.resolve("soft/api"),
                    assignedDir = dir,
                ),
                pastSession("00000000-0000-4000-8000-000000000003", plant.id, dir.resolve("docs")),
            )

        PastSessionCounts.of(plant, past) shouldBe PastCounts(total = 3, inSubfolders = 1, inRoot = 2)
    }
}
