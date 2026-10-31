@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

/** `SessionDetails.canAttach` and `canReply`, which the Attach and Odpowiedz controls follow (FR-K3, AM-511). */
internal class SessionOpenStateTest {
    private val project = aProject(id = "project0001", displayName = "Quayside")

    private fun session(
        state: SessionState,
        cwd: Path = A_PROJECT_DIR,
    ): WorldSession = WorldSession(SessionId("a"), cwd, state, "name-a", T0)

    private fun TestScope.start(vararg sessions: WorldSession): MissionControlController {
        val world = SessionsWorld(listOf(WorldProject(project.id, project.dir)), sessions.toList())
        val fake = FakeSessions(world, TestClock())
        return MissionControlController(
            SessionsApi(monitor = fake, commands = fake),
            FakeProjectsApi(initial = listOf(project)),
            MutableStateFlow(project.id),
            TestClock(),
            backgroundScope,
            NoOpLogger,
            1.hours,
        ).also { runCurrent() }
    }

    private fun TestScope.selected(controller: MissionControlController): SessionDetails? {
        controller.onEvent(MissionControlEvent.SelectSession(SessionId("a")))
        runCurrent()
        return when (val screen = controller.state.value.screen) {
            is Screen.Project -> screen.details
            is Screen.Global -> screen.details
            else -> null
        }
    }

    @Test
    fun `a working session can be attached but not replied to`() =
        runTest {
            val details = selected(start(session(SessionState.Working)))

            details?.canAttach shouldBe true
            details?.canReply shouldBe false
        }

    @Test
    fun `a session that waits for the user can be attached and replied to`() =
        runTest {
            val details = selected(start(session(SessionState.AwaitingPermission("Bash"))))

            details?.canAttach shouldBe true
            details?.canReply shouldBe true
        }

    @Test
    fun `a session outside the registered projects cannot be attached`() =
        runTest {
            val controller = start(session(SessionState.AwaitingInput(null), Path.of("/Users/test/elsewhere")))
            controller.onIntent(MissionControlIntent.ShowView(GlobalView.WAITING))
            runCurrent()

            val details = selected(controller)

            details?.canAttach shouldBe false
            details?.canReply shouldBe false
        }

    @Test
    fun `an open dialog switches attach and reply off`() =
        runTest {
            val controller = start(session(SessionState.Done))
            selected(controller)

            controller.onIntent(MissionControlIntent.RemoveSelected)
            runCurrent()

            val screen = controller.state.value.screen as Screen.Project
            screen.details?.canAttach shouldBe false
        }
}
