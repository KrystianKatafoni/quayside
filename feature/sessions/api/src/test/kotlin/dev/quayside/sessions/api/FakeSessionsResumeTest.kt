package dev.quayside.sessions.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * `FakeSessions.removeDirectory`: the missing start directory is not in `ResumeContract` (the real system checks it
 * through an injected disk check, `sessions:impl` has its own test), so the fake is pinned here.
 */
class FakeSessionsResumeTest {
    private val dir = P1_DIR.resolve("src")
    private val id = SessionId("c0de0001-0000-4000-8000-000000000000")

    private fun fake(): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects(P1 to P1_DIR),
                emptyList(),
                pastSessions = listOf(WorldPastSession(id, dir, T0, "title")),
            ),
        )

    @Test
    fun `a removed start directory is DIRECTORY_MISSING and the disk row stays`() =
        runTest {
            val fake = fake()
            fake.removeDirectory(dir)

            fake.resume(id) shouldBe Outcome.Err(SessionError.ResumeUnavailable(id, ResumeProblem.DIRECTORY_MISSING))

            fake.snapshot.value.sessions
                .map { it.id } shouldBe listOf(id)
        }

    @Test
    fun `a removed file wins over a removed directory`() =
        runTest {
            val fake = fake()
            fake.removeDirectory(dir)
            fake.removePastSession(id)

            // the row stays until the next scan, the file check comes first
            fake.resume(id) shouldBe Outcome.Err(SessionError.ResumeUnavailable(id, ResumeProblem.FILE_MISSING))
        }

    @Test
    fun `another directory does not block the resume`() =
        runTest {
            val fake = fake()
            fake.removeDirectory(P1_DIR.resolve("other"))

            fake.resume(id) shouldBe Outcome.Ok(ResumedSession(SessionId("c0de0001"), copy = false))
        }
}
