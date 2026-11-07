package dev.quayside.missioncontrol.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import dev.quayside.core.designsystem.TextRole
import dev.quayside.core.designsystem.toAmTypography
import dev.quayside.core.designtokens.TypographyId
import dev.quayside.core.designtokens.TypographySpec
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.aSession
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Where the NAME and LABEL roles are used (AM-816): session names in rows, details and bar; section labels. */
@OptIn(ExperimentalTestApi::class)
internal class TextRolesScreenTest {
    private val typography =
        TypographySpec(TypographyId("plex"), "IBM Plex", "IBM Plex Sans", "IBM Plex Mono").toAmTypography()
    private val name = typography.style(TextRole.NAME)
    private val label = typography.style(TextRole.LABEL)
    private val one = SessionId("one")

    private fun sessions() = fakeSessions(worldSession("one", SessionState.AwaitingInput("q"), name = "fix-login"))

    private fun androidx.compose.ui.test.ComposeUiTest.textIn(
        tag: String,
        text: String,
    ): SemanticsNodeInteraction = onAllNodes(hasText(text) and inTag(tag), useUnmergedTree = true).onFirst()

    private infix fun TextStyle.sameFaceAs(expected: TextStyle) {
        fontFamily shouldBe expected.fontFamily
        fontSize shouldBe expected.fontSize
        fontWeight shouldBe expected.fontWeight
    }

    @Test
    fun `row name uses the name role`() =
        runComposeUiTest {
            showMc(sessions())
            awaitTag(MissionControlTags.row(one))

            textIn(MissionControlTags.row(one), "fix-login").textStyle() sameFaceAs name
        }

    @Test
    fun `details name uses the name role`() =
        runComposeUiTest {
            showMc(sessions()).pickSession("one")
            awaitTag(MissionControlTags.DETAILS)

            textIn(MissionControlTags.DETAILS, "fix-login").textStyle() sameFaceAs name
        }

    @Test
    fun `session bar name uses the name role`() =
        runComposeUiTest {
            showMc(sessions()) { controller -> SessionBar(controller, one, onReply = {}) }.pickSession("one")
            awaitTag(MissionControlTags.SESSION_BAR)

            textIn(MissionControlTags.SESSION_BAR, "fix-login").textStyle() sameFaceAs name
        }

    @Test
    fun `summary label is uppercase label role`() =
        runComposeUiTest {
            val session = aSession(id = "one", project = MC_PROJECT.id, summary = "Fixing the login form")
            showScripted(freshSnapshot(session)).pickSession("one")
            awaitTag(MissionControlTags.DETAILS_SUMMARY)

            val shown = textIn(MissionControlTags.DETAILS_SUMMARY, "PODSUMOWANIE")

            shown.textStyle() sameFaceAs label
            shown.textStyle().letterSpacing shouldBe label.letterSpacing
        }

    @Test
    fun `views label is uppercase label role`() =
        runComposeUiTest {
            showScripted(freshSnapshot()) { GlobalViews(it) }
            waitUntil { onAllNodes(hasText("WIDOKI")).fetchSemanticsNodes().isNotEmpty() }

            val shown = onAllNodes(hasText("WIDOKI"), useUnmergedTree = true).onFirst()

            shown.textStyle() sameFaceAs label
            shown.textStyle().letterSpacing shouldBe label.letterSpacing
            // above the first entry
            val above = shown.fetchSemanticsNode().boundsInRoot.bottom
            val entry = awaitTag(MissionControlTags.view(GlobalView.WAITING)).fetchSemanticsNode().boundsInRoot.top
            (above <= entry) shouldBe true
        }
}
