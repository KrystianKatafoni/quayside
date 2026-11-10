package dev.quayside.missioncontrol.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmTheme
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.sessions.api.FakeSessionHistorySettings
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.HistoryLimit
import dev.quayside.sessions.api.SessionHistorySettings
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldPastSession
import dev.quayside.sessions.api.WorldProject
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The badge "z dysku" and the row "jeszcze N starszych" in the list of Mission Control (FR-P9, NFR-6). */
@OptIn(ExperimentalTestApi::class)
internal class PastSessionRowScreenTest {
    private val first = SessionId("00000000-0000-4000-8000-000000000001")

    private fun past(n: Int) =
        WorldPastSession(
            SessionId("00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"),
            MC_PROJECT.dir,
            T0.minusSeconds(n * 60L),
            "chat $n",
        )

    /** [count] old sessions of [MC_PROJECT] and a count limit of 50. */
    private fun sessions(count: Int): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(MC_PROJECT.id, MC_PROJECT.dir)),
                pastSessions = (1..count).map(::past),
            ),
            TestClock(T0),
            FakeSessionHistorySettings(SessionHistorySettings(maxPerProject = HistoryLimit.SESSIONS_50)),
        )

    @Test
    fun `the badge says z dysku, is read with a description and is a neutral chip`() =
        runComposeUiTest {
            // The neutral chip: its label meets 4.5:1 on the raised fill in every theme (app ScreenContrastTest).
            var raised = 0
            showMc(sessions(1)) {
                raised =
                    AmTheme.colors.surfaces.raised
                        .toColor()
                        .toArgb()
                MissionControl(it)
            }
            val badge = awaitTag(HistoryTags.rowDisk(first))

            onAllNodes(
                hasText("z dysku") and hasAnyAncestorOrSelf(HistoryTags.rowDisk(first)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty() shouldBe true
            onAllNodes(
                hasContentDescription(mcText("row.fromDisk.description")) and
                    hasAnyAncestorOrSelf(HistoryTags.rowDisk(first)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty() shouldBe true
            (raised in badge.argbColors()) shouldBe true
        }

    @Test
    fun `the older row says how many are left out and its link calls the handler`() =
        runComposeUiTest {
            var clicks = 0
            showMc(sessions(52), MissionControlHandlers(onChangeHistoryLimit = { clicks++ }))
            awaitTag(HistoryTags.OLDER_ROW)

            textUnder(HistoryTags.OLDER_ROW, "jeszcze 2 starsze").assertExists()
            awaitTag(HistoryTags.OLDER_CHANGE).performScrollTo().performClick()

            clicks shouldBe 1
        }

    @Test
    fun `no older row when the limit cut nothing`() =
        runComposeUiTest {
            showMc(sessions(3))
            awaitTag(HistoryTags.rowDisk(first))

            hasTag(HistoryTags.OLDER_ROW) shouldBe false
        }

    @Test
    fun `the remove dialog of a disk session says its file stays on disk`() =
        runComposeUiTest {
            val controller = showMc(sessions(1))
            awaitTag(HistoryTags.rowDisk(first))

            runOnIdle { controller.onEvent(MissionControlEvent.SelectSession(first)) }
            runOnIdle { controller.onIntent(MissionControlIntent.RemoveSelected) }
            awaitTag(MissionControlTags.CONFIRM_REMOVE)
            textUnder(MissionControlTags.CONFIRM_REMOVE, mcText("confirmRemove.hint.disk")).assertExists()
            hasTextUnder(mcText("confirmRemove.hint")) shouldBe false
        }

    @Test
    fun `the remove dialog of a supervisor session keeps the old hint`() =
        runComposeUiTest {
            val controller = showMc(fakeSessions(worldSession("stopped1", SessionState.Stopped)))
            awaitTag(MissionControlTags.row(SessionId("stopped1")))

            runOnIdle { controller.pickSession("stopped1") }
            runOnIdle { controller.onIntent(MissionControlIntent.RemoveSelected) }
            awaitTag(MissionControlTags.CONFIRM_REMOVE)
            textUnder(MissionControlTags.CONFIRM_REMOVE, mcText("confirmRemove.hint")).assertExists()
            hasTextUnder(mcText("confirmRemove.hint.disk")) shouldBe false
        }

    private fun androidx.compose.ui.test.ComposeUiTest.hasTextUnder(text: String): Boolean =
        onAllNodes(hasText(text) and hasAnyAncestorOrSelf(MissionControlTags.CONFIRM_REMOVE), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun hasAnyAncestorOrSelf(tag: String): SemanticsMatcher =
        hasTestTag(tag) or
            androidx.compose.ui.test
                .hasAnyAncestor(hasTestTag(tag))
}
