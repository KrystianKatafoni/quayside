package dev.quayside.missioncontrol.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Entries of the global views in the sidebar column (plan AM-809). */
@OptIn(ExperimentalTestApi::class)
internal class GlobalViewEntryScreenTest {
    private val tokens = SampleTokens.colors()

    @Test
    fun `view entry shows icon and count chip with description`() =
        runComposeUiTest {
            // Given two waiting sessions
            showMc(
                fakeSessions(
                    worldSession("a", SessionState.AwaitingInput("q"), index = 1),
                    worldSession("b", SessionState.AwaitingPermission("Bash"), index = 2),
                ),
            ) { GlobalViews(it) }
            val entry = MissionControlTags.view(GlobalView.WAITING)

            // Then the entry has a chip on the warn background, and the chip is described with the count: a
            // description that is more than the bare "2"
            val shown = awaitTag(entry).argbColors()
            shown shouldContain
                tokens.status.warnBg
                    .toColor()
                    .toArgb()
            val described =
                onAllNodes(hasContentDescription("2", substring = true) and hasAnyAncestor(hasTestTag(entry)), true)
                    .fetchSemanticsNodes()
            described.size shouldBe 1
            val description = described.single().config[SemanticsProperties.ContentDescription]
            (description.single() != "2") shouldBe true
        }
}
