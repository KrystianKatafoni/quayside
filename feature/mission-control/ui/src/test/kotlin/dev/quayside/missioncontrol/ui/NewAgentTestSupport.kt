@file:OptIn(ExperimentalTestApi::class)

package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.ui.test.waitUntilExactlyOneExists
import dev.quayside.core.designsystem.AmDropdownTags
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.domain.Logger
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import kotlinx.coroutines.CompletableDeferred
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [SessionCommands] over [inner] that records every `start` ([specs], in order) before it runs. With a [gate] the
 * start waits until the test completes it, which gives the "start runs" state without a real clock. Not a change of
 * `FakeSessions`: the `testFixtures` of `sessions:api` stay as they are (AM-608).
 */
internal class RecordingCommands(
    private val inner: SessionCommands,
    private val gate: CompletableDeferred<Unit>? = null,
) : SessionCommands by inner {
    private val recorded = CopyOnWriteArrayList<Pair<ProjectId, NewSessionSpec>>()

    val specs: List<Pair<ProjectId, NewSessionSpec>> get() = recorded.toList()

    override suspend fun start(
        project: ProjectId,
        spec: NewSessionSpec,
    ): Outcome<SessionId, SessionError> {
        recorded += project to spec
        gate?.await()
        return inner.start(project, spec)
    }
}

/** Two projects, both known to the fake sessions, so a start in either works. */
internal object NewAgentWorld {
    val QUAYSIDE: Project =
        aProject(id = "quayside", dir = Path.of("/Users/test/projects/quayside"), displayName = "Quayside")
    val OTHER: Project = aProject(id = "other", dir = Path.of("/Users/test/projects/other"), displayName = "Other")
    val PROJECTS: List<Project> = listOf(QUAYSIDE, OTHER)

    /** A group (FR-P7): the names differ in case so that the order is not the ASCII one; `Carl` is gone from disk. */
    val GROUP: Project =
        aProject(
            id = "group",
            dir = Path.of("/Users/test/projects/group"),
            displayName = "Group",
            subfolders = subfolderNames("Anna", "bob", "Carl"),
        )
    val ANNA = SubfolderName("Anna")
    val BOB = SubfolderName("bob")
    val CARL = SubfolderName("Carl")

    /** [PROJECTS] and the [GROUP] known to the fake sessions, so a start in any of them works. */
    fun sessions(): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects =
                    PROJECTS.map { WorldProject(it.id, it.dir) } +
                        WorldProject(
                            GROUP.id,
                            GROUP.dir,
                            GROUP.subfolders.map { it.value },
                            missing = setOf(CARL.value),
                        ),
            ),
        )

    fun projects(): FakeProjectsApi = FakeProjectsApi(initial = PROJECTS)

    /** The list of [projects] plus the [GROUP] as its third entry. */
    fun projectsWithGroup(): FakeProjectsApi = FakeProjectsApi(initial = PROJECTS + GROUP)

    /** Texts that must never reach a log, a failure text or a `toString`. */
    const val SECRET_PROMPT = "secret-prompt-xyz"
    const val SECRET_NAME = "secret-name-xyz"
    const val SECRET_AGENT = "secret-agent-xyz"
}

/** Everything the log of the [RecordingLogger] holds as one text, for "never contains" assertions. */
internal fun RecordingLogger.dump(): String =
    entries.joinToString(separator = "\n") { "${it.level} ${it.message} ${it.fields} ${it.cause}" }

/** What a Compose test needs to drive a [NewAgentController] shown by [NewAgentDialog]. */
internal class NewAgentRig(
    val commands: RecordingCommands,
    val log: RecordingLogger = RecordingLogger(),
) {
    val started = CopyOnWriteArrayList<Pair<ProjectId, SessionId>>()

    /** The focus `onStarted` was called with, in order (the project of each is in [started]). */
    val focuses = CopyOnWriteArrayList<ProjectFocus>()
    var controller: NewAgentController? = null
}

/** Shows [NewAgentDialog] over a controller that lives in the composition scope. The dialog starts closed. */
internal fun ComposeUiTest.showNewAgent(
    rig: NewAgentRig,
    logger: Logger = rig.log,
    projects: FakeProjectsApi = NewAgentWorld.projects(),
) {
    setContent {
        AmTestTheme {
            val scope = rememberCoroutineScope()
            val controller =
                remember {
                    NewAgentController(rig.commands, projects, scope, logger) { focus, id ->
                        rig.started += focus.project to id
                        rig.focuses += focus
                    }.also { rig.controller = it }
                }
            NewAgentDialog(controller)
        }
    }
    waitUntil { rig.controller != null }
}

/** Opens the dialog of [rig] with [project] (and its [subfolder]) preselected and waits until it is drawn. */
internal fun ComposeUiTest.openNewAgent(
    rig: NewAgentRig,
    project: ProjectId? = NewAgentWorld.QUAYSIDE.id,
    subfolder: SubfolderName? = null,
) {
    runOnIdle { checkNotNull(rig.controller).open(project?.let { ProjectFocus(it, subfolder) }) }
    waitUntilExactlyOneExists(hasTestTag(MissionControlTags.NEW_AGENT_DIALOG))
}

/** The form now in the controller of [rig]; fails when the dialog is closed. */
internal fun NewAgentRig.form(): NewAgentForm = checkNotNull(checkNotNull(controller).state.value).form

/** The state now in the controller of [rig], null while closed. */
internal fun NewAgentRig.state(): NewAgentState? = checkNotNull(controller).state.value

internal fun ComposeUiTest.await(tag: String): SemanticsNodeInteraction {
    waitUntilExactlyOneExists(hasTestTag(tag))
    return onNodeWithTag(tag)
}

internal fun ComposeUiTest.awaitFocus(tag: String) {
    waitUntilExactlyOneExists(hasTestTag(tag) and isFocused(), FOCUS_WAIT_MS)
}

internal fun ComposeUiTest.awaitGone(tag: String) = waitUntilDoesNotExist(hasTestTag(tag), FOCUS_WAIT_MS)

/** A key with ⌘ held, e.g. ⌘⏎. */
internal fun SemanticsNodeInteraction.pressWithMeta(key: Key) {
    performKeyInput {
        keyDown(Key.MetaLeft)
        pressKey(key)
        keyUp(Key.MetaLeft)
    }
}

/** Shift and a key, e.g. Shift+Tab. */
internal fun SemanticsNodeInteraction.pressWithShift(key: Key) {
    performKeyInput {
        keyDown(Key.ShiftLeft)
        pressKey(key)
        keyUp(Key.ShiftLeft)
    }
}

internal fun SemanticsNodeInteraction.press(key: Key) {
    performKeyInput { pressKey(key) }
}

/** Gives the field the focus the way Tab does, without a click (a click on a dropdown field opens its list). */
internal fun SemanticsNodeInteraction.focus() = performSemanticsAction(SemanticsActions.RequestFocus)

/** Waits until a dropdown list is open. */
internal fun ComposeUiTest.awaitListOpen() = waitUntilExactlyOneExists(hasTestTag(AmDropdownTags.LIST), FOCUS_WAIT_MS)

/** Clicks the dropdown field [tag] and waits until its list is open. */
internal fun ComposeUiTest.openList(tag: String) {
    onNodeWithTag(tag).performClick()
    awaitListOpen()
}

/** Waits until no dropdown list is open. */
internal fun ComposeUiTest.awaitListGone() = awaitGone(AmDropdownTags.LIST)

/** Opens the list of the dropdown field [tag] with a click, clicks its option [index] and waits for the close. */
internal fun ComposeUiTest.pickOption(
    tag: String,
    index: Int,
) {
    openList(tag)
    onNodeWithTag(AmDropdownTags.option(index)).performClick()
    awaitListGone()
}

/** Sends [key] to the open dropdown list, or to the field [field] when no list is open. */
internal fun ComposeUiTest.pressKeyOn(
    field: String,
    key: Key,
) {
    val open = onAllNodesWithTag(AmDropdownTags.LIST).fetchSemanticsNodes().isNotEmpty()
    onNodeWithTag(if (open) AmDropdownTags.LIST else field).press(key)
    waitForIdle()
}

internal const val FOCUS_WAIT_MS = 5_000L

/** The text of the MC bundle for [key]. */
internal fun text(key: String): String = MissionControlStrings[key]

/** Opens [this] with the project of [project] as a whole (no subfolder); for tests written before groups (AM-308). */
internal fun NewAgentController.open(project: ProjectId?) = open(project?.let { ProjectFocus(it) })
