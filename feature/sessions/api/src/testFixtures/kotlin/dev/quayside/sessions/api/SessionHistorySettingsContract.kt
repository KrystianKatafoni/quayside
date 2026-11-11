package dev.quayside.sessions.api

import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Behaviour every [SessionHistorySettingsRepo] must have (ENG-T2). Runs on [FakeSessionHistorySettings] and on the
 * JSON-file implementation (`session-history.json`) over a temporary directory.
 */
public abstract class SessionHistorySettingsContract {
    /** A repo with nothing stored. */
    protected abstract suspend fun create(): SessionHistorySettingsRepo

    @Test
    public fun `nothing stored gives the defaults`(): Unit =
        runTest {
            val settings = create().settings.value

            settings shouldBe SessionHistorySettings()
            settings.maxAge shouldBe HistoryAge.DAYS_30
            settings.maxPerProject shouldBe HistoryLimit.SESSIONS_200
            settings.hidden shouldBe emptySet()
        }

    @Test
    public fun `update saves, publishes and returns the new settings`(): Unit =
        runTest {
            val repo = create()

            val result = repo.update { it.copy(maxAge = HistoryAge.DAYS_7) }

            val expected = SessionHistorySettings(maxAge = HistoryAge.DAYS_7)
            result shouldBe Outcome.Ok(expected)
            repo.settings.value shouldBe expected
        }

    @Test
    public fun `update changes one value and keeps the others`(): Unit =
        runTest {
            val repo = create()
            repo.update { it.copy(maxAge = HistoryAge.UNLIMITED) }

            repo.update { it.copy(maxPerProject = HistoryLimit.SESSIONS_1000) }

            repo.settings.value shouldBe
                SessionHistorySettings(HistoryAge.UNLIMITED, HistoryLimit.SESSIONS_1000, emptySet())
        }

    @Test
    public fun `hidden ids accumulate`(): Unit =
        runTest {
            val repo = create()

            repo.update { it.copy(hidden = it.hidden + SessionId("aaaa")) }
            repo.update { it.copy(hidden = it.hidden + SessionId("bbbb")) }
            repo.update { it.copy(hidden = it.hidden + SessionId("aaaa")) }

            repo.settings.value.hidden shouldBe setOf(SessionId("aaaa"), SessionId("bbbb"))
        }

    @Test
    public fun `a change that changes nothing is ok`(): Unit =
        runTest {
            val repo = create()

            repo.update { it } shouldBe Outcome.Ok(SessionHistorySettings())
        }

    @Test
    public fun `places round trip through update`(): Unit =
        runTest {
            val repo = create()
            val places =
                mapOf(
                    SessionId("0b6f0000-0000-4000-8000-000000000000") to
                        StoredPlace(ProjectId("p1"), PlaceAssignment.Subfolder("soft")),
                    SessionId("7c5dcf5d") to StoredPlace(ProjectId("p1"), PlaceAssignment.ProjectRoot),
                )

            val result = repo.update { it.copy(places = places) }

            result shouldBe Outcome.Ok(SessionHistorySettings(places = places))
            repo.settings.value.places shouldBe places
            repo.update { it.copy(maxAge = HistoryAge.DAYS_7) }
            repo.settings.value.places shouldBe places
        }
}
