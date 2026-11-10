package dev.quayside.sessions.api

import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** The scripting of [FakeSessions] for disk sessions that window and controller tests rely on (FR-P9). */
class FakeSessionsPastTest {
    private val project = ProjectId("p1")
    private val dir: Path = Path.of("/Users/test/projects/p1")
    private val past = WorldPastSession(SessionId("00000000-0000-4000-8000-000000000001"), dir, T0.minusSeconds(60))

    private fun fake(vararg files: WorldPastSession) =
        FakeSessions(SessionsWorld(projects = listOf(WorldProject(project, dir)), pastSessions = files.toList()))

    @Test
    fun `a paused scan holds back the result until it is resumed`() {
        val fake = fake(past)
        fake.pauseScan()
        fake.addPastSession(past.copy(id = SessionId("00000000-0000-4000-8000-000000000002")))
        val before = fake.snapshot.value

        fake.resumeScan()

        before.history.size shouldBe 1
        fake.snapshot.value.history shouldBe mapOf(project to ProjectHistory(2, 0))
    }

    @Test
    fun `a scan paused before a file appears lists it only after the resume`() {
        val fake =
            FakeSessions(SessionsWorld(projects = listOf(WorldProject(project, dir)), pastSessions = listOf(past)))
        val paused = FakeSessions(SessionsWorld(projects = listOf(WorldProject(project, dir))))
        paused.pauseScan()
        paused.addPastSession(past)

        paused.snapshot.value.sessions.size shouldBe 0
        fake.snapshot.value.sessions.size shouldBe 1
        paused.resumeScan()
        paused.snapshot.value.sessions.size shouldBe 1
    }

    @Test
    fun `a settings change shows in the snapshot at once`() =
        runTest {
            val fake = fake(past.copy(modifiedAt = T0.minusSeconds(10L * 24 * 3600)))
            fake.snapshot.value.sessions.size shouldBe 1

            fake.historySettings.update { it.copy(maxAge = HistoryAge.DAYS_7) }

            fake.snapshot.value.sessions.size shouldBe 0
            fake.snapshot.value.history shouldBe mapOf(project to ProjectHistory(0, 0))
        }
}
