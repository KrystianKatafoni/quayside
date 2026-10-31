@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)

package dev.quayside.app

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.domain.ShellCommand
import dev.quayside.core.testing.RecordingLogger
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.missioncontrol.ui.MissionControlController
import dev.quayside.missioncontrol.ui.MissionControlIntent
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import dev.quayside.terminal.ui.TerminalArea
import dev.quayside.terminal.ui.TerminalController
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

/**
 * Composes the selected session, `attachCommand` and the terminal (FR-K2, AM-505, AM-506). One session `s1` named
 * `fix-login` works (or waits, for reply tests) in the project `Quayside`; `j` selects it.
 * `claude attach <id>` runs in the temp dir, which exists.
 */
class SessionAttacherTest {
    @TempDir
    lateinit var root: Path

    private val project = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Quayside")
    private val terminals = RecordingTerminals()
    private val log = RecordingLogger()
    private val secretName = "fix-login"

    private fun attachCommand(id: SessionId) = ShellCommand(listOf("claude", "attach", id.value), root)

    private fun sessions(state: SessionState): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(project.id, project.dir)),
                sessions =
                    listOf(
                        WorldSession(
                            id = SessionId("s1"),
                            cwd = A_PROJECT_DIR,
                            state = state,
                            name = secretName,
                            startedAt = T0,
                        ),
                    ),
            ),
            TestClock(T0),
        )

    private fun missionControl(
        scope: CoroutineScope,
        state: SessionState = SessionState.Working,
    ): MissionControlController {
        val fake = sessions(state)
        return MissionControlController(
            SessionsApi(monitor = fake, commands = fake),
            FakeProjectsApi(initial = listOf(project)),
            MutableStateFlow(project.id),
            TestClock(T0),
            scope,
            NoOpLogger,
            1.hours,
        )
    }

    private class Parts(
        val missionControl: MissionControlController,
        val terminal: TerminalController,
        val attacher: SessionAttacher,
    )

    private fun TestScope.parts(state: SessionState = SessionState.Working): Parts {
        val missionControl = missionControl(backgroundScope, state)
        val terminal = TerminalController(terminals, backgroundScope, NoOpLogger, "zsh")
        return Parts(missionControl, terminal, SessionAttacher(missionControl, terminal, ::attachCommand, log))
            .also { runCurrent() }
    }

    @Test
    fun `attach opens claude attach of the selected session in its project group`() =
        runTest {
            val parts = parts()
            parts.missionControl.onIntent(MissionControlIntent.NextSession)

            parts.attacher.attachSelected()
            runCurrent()

            terminals.fake.commands shouldBe listOf(attachCommand(SessionId("s1")))
            parts.terminal.menu.value.hasTabs shouldBe true
        }

    @Test
    fun `attach labels the tab with the session name`() =
        runComposeUiTest {
            lateinit var attacher: SessionAttacher
            lateinit var missionControl: MissionControlController
            setContent {
                val scope = rememberCoroutineScope()
                missionControl = remember { missionControl(scope) }
                val terminal = remember { TerminalController(terminals, scope, NoOpLogger, "zsh") }
                attacher = remember { SessionAttacher(missionControl, terminal, ::attachCommand, log) }
                TerminalArea(terminal, { false }, Modifier, terminals.paneContent)
            }
            waitForIdle()

            runOnIdle {
                missionControl.onIntent(MissionControlIntent.NextSession)
                attacher.attachSelected()
            }

            await(WindowTags.terminalTab(1)).assertTextContains("attach · $secretName", substring = true)
            await(WindowTags.terminalGroup("project0001")).assertTextContains("Quayside", substring = true)
        }

    @Test
    fun `attach without a target opens nothing`() =
        runTest {
            val parts = parts()

            parts.attacher.attachSelected()
            runCurrent()

            terminals.fake.commands.shouldBeEmpty()
            parts.terminal.menu.value.hasTabs shouldBe false
            log.entries.shouldBeEmpty()
        }

    @Test
    fun `attach logs the session id only`() =
        runTest {
            val parts = parts()
            parts.missionControl.onIntent(MissionControlIntent.NextSession)

            parts.attacher.attachSelected()
            runCurrent()

            log.entries.map { it.level to it.message } shouldBe listOf(LogLevel.INFO to "session attach")
            log.entries.map { it.fields } shouldBe listOf(mapOf("sessionId" to "s1"))
            val logged = log.entries.joinToString { "${it.message} ${it.fields} ${it.cause}" }
            logged shouldNotContain secretName
            logged shouldNotContain A_PROJECT_DIR.toString()
            logged shouldNotContain "Quayside"
        }

    @Test
    fun `reply opens claude attach of a waiting session in its project group`() =
        runTest {
            val parts = parts(SessionState.AwaitingInput("Continue?"))
            parts.missionControl.onIntent(MissionControlIntent.NextSession)

            parts.attacher.replySelected()
            runCurrent()

            terminals.fake.commands shouldBe listOf(attachCommand(SessionId("s1")))
            parts.terminal.menu.value.hasTabs shouldBe true
        }

    @Test
    fun `reply labels the tab like attach`() =
        runComposeUiTest {
            lateinit var attacher: SessionAttacher
            lateinit var missionControl: MissionControlController
            setContent {
                val scope = rememberCoroutineScope()
                missionControl = remember { missionControl(scope, SessionState.AwaitingPermission("Bash")) }
                val terminal = remember { TerminalController(terminals, scope, NoOpLogger, "zsh") }
                attacher = remember { SessionAttacher(missionControl, terminal, ::attachCommand, log) }
                TerminalArea(terminal, { false }, Modifier, terminals.paneContent)
            }
            waitForIdle()

            runOnIdle {
                missionControl.onIntent(MissionControlIntent.NextSession)
                attacher.replySelected()
            }

            await(WindowTags.terminalTab(1)).assertTextContains("attach · $secretName", substring = true)
            await(WindowTags.terminalGroup("project0001")).assertTextContains("Quayside", substring = true)
        }

    @Test
    fun `reply of a session that is not waiting opens nothing`() =
        runTest {
            // The guard of decision 3: the target exists (Working), but only attach may open it.
            val parts = parts(SessionState.Working)
            parts.missionControl.onIntent(MissionControlIntent.NextSession)
            parts.missionControl.attachTarget()?.waiting shouldBe false

            parts.attacher.replySelected()
            runCurrent()

            terminals.fake.commands.shouldBeEmpty()
            parts.terminal.menu.value.hasTabs shouldBe false
            log.entries.shouldBeEmpty()
        }

    @Test
    fun `reply without a target opens nothing`() =
        runTest {
            val parts = parts(SessionState.AwaitingInput(null))

            parts.attacher.replySelected()
            runCurrent()

            terminals.fake.commands.shouldBeEmpty()
            parts.terminal.menu.value.hasTabs shouldBe false
            log.entries.shouldBeEmpty()
        }

    @Test
    fun `reply logs the session id only`() =
        runTest {
            val parts = parts(SessionState.AwaitingInput("Continue?"))
            parts.missionControl.onIntent(MissionControlIntent.NextSession)

            parts.attacher.replySelected()
            runCurrent()

            log.entries.map { it.level to it.message } shouldBe listOf(LogLevel.INFO to "session reply")
            log.entries.map { it.fields } shouldBe listOf(mapOf("sessionId" to "s1"))
            val logged = log.entries.joinToString { "${it.message} ${it.fields} ${it.cause}" }
            logged shouldNotContain secretName
            logged shouldNotContain A_PROJECT_DIR.toString()
            logged shouldNotContain "Quayside"
            logged shouldNotContain "Continue?"
        }

    @Test
    fun `attach of a session with a live tab opens no second process`() =
        runTest {
            val parts = parts()
            parts.missionControl.onIntent(MissionControlIntent.NextSession)
            parts.attacher.attachSelected()
            runCurrent()

            parts.attacher.attachSelected()
            runCurrent()

            terminals.fake.commands shouldBe listOf(attachCommand(SessionId("s1")))
            terminals.fake.sessions.size shouldBe 1
        }

    @Test
    fun `reply of a session with a live attach tab opens no second process`() =
        runTest {
            val parts = parts(SessionState.AwaitingInput("Continue?"))
            parts.missionControl.onIntent(MissionControlIntent.NextSession)
            parts.attacher.attachSelected()
            runCurrent()

            parts.attacher.replySelected()
            runCurrent()

            terminals.fake.commands shouldBe listOf(attachCommand(SessionId("s1")))
            terminals.fake.sessions.size shouldBe 1
        }

    @Test
    fun `attach of a session whose tab exited reopens it in place`() =
        runTest {
            val parts = parts()
            parts.missionControl.onIntent(MissionControlIntent.NextSession)
            parts.attacher.attachSelected()
            runCurrent()
            terminals.fake.sessions
                .single()
                .finish(0)
            runCurrent()

            parts.attacher.attachSelected()
            runCurrent()

            terminals.fake.commands shouldBe List(2) { attachCommand(SessionId("s1")) }
            terminals.fake.sessions
                .map { it.isClosed } shouldBe listOf(true, false)
        }
}
