package dev.quayside.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.commandpalette.ui.CommandPaletteController
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.terminal.ui.TerminalKey
import java.awt.event.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [SessionCommands] over [inner] that records every `start` ([specs], in order) before it runs, so a window test sees
 * the model, mode, subagent and name that reached it. `FakeSessions` itself records only `start <project>`.
 */
internal class RecordingStarts(
    private val inner: SessionCommands,
) : SessionCommands by inner {
    private val recorded = CopyOnWriteArrayList<Pair<ProjectId, NewSessionSpec>>()

    val specs: List<Pair<ProjectId, NewSessionSpec>> get() = recorded.toList()

    override suspend fun start(
        project: ProjectId,
        spec: NewSessionSpec,
    ): Outcome<SessionId, SessionError> {
        recorded += project to spec
        return inner.start(project, spec)
    }
}

/** Two projects known to the fake sessions, behind a [RecordingStarts]; the first started session is `00000001`. */
internal class AgentWorld {
    val quayside = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    val other = aProject(id = "project0002", dir = A_PROJECT_DIR.resolveSibling("other"), displayName = "Other")
    val fake =
        FakeSessions(SessionsWorld(projects = listOf(quayside, other).map { WorldProject(it.id, it.dir) }))
    val starts = RecordingStarts(fake)
    val setup = WindowSetup(FakeProjectsApi(initial = listOf(quayside, other)), fake, starts = starts)

    /** The id `FakeSessions` gives the first started session. */
    val first = SessionId("00000001")
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitFocus(tag: String) {
    waitUntilExactlyOneExists(hasTestTag(tag) and isFocused(), AGENT_WAIT_MS)
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitGone(tag: String) = waitUntilDoesNotExist(hasTestTag(tag), AGENT_WAIT_MS)

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitText(
    tag: String,
    text: String,
) = waitUntilExactlyOneExists(hasTestTag(tag) and hasText(text, substring = true), AGENT_WAIT_MS)

/** The details panel of the window shows [text] at least once (the id is also the name of an unnamed session). */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitDetails(text: String) =
    waitUntilAtLeastOneExists(
        hasText(text, substring = true) and hasAnyAncestor(hasTestTag(WindowTags.DETAILS)),
        AGENT_WAIT_MS,
    )

/** Presses ⌘N the way a focused terminal does: through the bridge that runs the command bound to the chord. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.pressCmdN(setup: WindowSetup) {
    runOnIdle {
        val handled = checkNotNull(setup.keys).onKey(TerminalKey(KeyEvent.VK_N, true, false, false, false))
        check(handled) { "cmd N is bound to no command of the window" }
    }
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openFromPalette(palette: CommandPaletteController) {
    runOnIdle { palette.open() }
    await(WindowTags.PALETTE_FIELD).performTextInput("Nowy agent")
    await(WindowTags.paletteRow("sessions.new")).performClick()
    waitUntilDoesNotExist(hasTestTag(WindowTags.PALETTE), AGENT_WAIT_MS)
}

/** Opens the dialog with the project [row] selected: click the project in the sidebar, then ⌘N. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openWithProject(
    setup: WindowSetup,
    row: String,
) {
    await(WindowTags.projectRow(row)).performClick()
    await(WindowTags.DETAILS_EMPTY)
    pressCmdN(setup)
    await(WindowTags.NEW_AGENT_DIALOG)
    awaitFocus(WindowTags.NEW_AGENT_PROMPT)
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.typePrompt(text: String) {
    onNodeWithTag(WindowTags.NEW_AGENT_PROMPT).performTextInput(text)
    waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.press(key: Key) {
    performKeyInput { pressKey(key) }
}

@OptIn(ExperimentalTestApi::class)
internal fun SemanticsNodeInteraction.pressWithMeta(key: Key) {
    performKeyInput {
        keyDown(Key.MetaLeft)
        pressKey(key)
        keyUp(Key.MetaLeft)
    }
}

internal const val AGENT_WAIT_MS = 5_000L
