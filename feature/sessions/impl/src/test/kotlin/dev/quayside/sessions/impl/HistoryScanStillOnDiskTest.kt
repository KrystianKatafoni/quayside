package dev.quayside.sessions.impl

import dev.quayside.agentruntime.api.ConversationId
import dev.quayside.agentruntime.api.FakeSessionHistory
import dev.quayside.agentruntime.api.HistoryCall
import dev.quayside.agentruntime.api.HistoryError
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.FakeSessionHistorySettings
import dev.quayside.sessions.api.pastSession
import dev.quayside.sessions.impl.MonitorFixtures.P1
import dev.quayside.sessions.impl.MonitorFixtures.P1_DIR
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** `HistoryScan.stillOnDisk`: the fresh check that makes a resume refuse a session whose file is gone. */
class HistoryScanStillOnDiskTest {
    private val sub = P1_DIR.resolve("src")
    private val disk = pastSession(id = uuidOf(1), project = P1, cwd = sub)

    private fun scan(history: FakeSessionHistory) =
        HistoryScan(
            history,
            FakeSessionHistorySettings(),
            FakeProjectsApi(listOf(Project(ProjectId("p1"), P1_DIR, "p1"))),
            TestClock(),
            NoOpLogger,
        )

    @Test
    fun `a file that is there is still on the disk`() =
        runTest {
            val history = FakeSessionHistory(listOf(conversation(1, cwd = sub)))

            scan(history).stillOnDisk(disk) shouldBe Outcome.Ok(true)
        }

    @Test
    fun `lists the start directory of the session without an age limit`() =
        runTest {
            val history = FakeSessionHistory(listOf(conversation(1, cwd = sub)))

            scan(history).stillOnDisk(disk)

            history.calls shouldBe listOf(HistoryCall(listOf(sub), since = null))
        }

    @Test
    fun `a deleted file is not on the disk`() =
        runTest {
            val history = FakeSessionHistory(listOf(conversation(1, cwd = sub)))
            history.delete(ConversationId(uuidOf(1)))

            scan(history).stillOnDisk(disk) shouldBe Outcome.Ok(false)
        }

    @Test
    fun `another conversation in the same directory does not count`() =
        runTest {
            val history = FakeSessionHistory(listOf(conversation(2, cwd = sub)))

            scan(history).stillOnDisk(disk) shouldBe Outcome.Ok(false)
        }

    @Test
    fun `the same UUID that started in a subdirectory does not count`() =
        runTest {
            // list(dirs) also returns files that started below a dir; the resume would run in the wrong directory
            val history = FakeSessionHistory(listOf(conversation(1, cwd = sub.resolve("deeper"))))

            scan(history).stillOnDisk(disk) shouldBe Outcome.Ok(false)
        }

    @Test
    fun `an unreadable history is an error, not a missing file`() =
        runTest {
            val history = FakeSessionHistory(listOf(conversation(1, cwd = sub)))
            history.failNext(HistoryError.UNREADABLE)

            scan(history).stillOnDisk(disk) shouldBe Outcome.Err(HistoryError.UNREADABLE)
        }
}
