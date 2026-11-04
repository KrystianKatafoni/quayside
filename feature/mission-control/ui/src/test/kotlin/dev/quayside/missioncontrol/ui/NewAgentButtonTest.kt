package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.testing.TestClock
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.Project
import dev.quayside.sessions.api.SessionsApi
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

/** The "Nowy agent ⌘N" button in the header of the project page (FR-K3, docs/12 §4 scenario 3, AM-608 rule 8). */
@OptIn(ExperimentalTestApi::class)
internal class NewAgentButtonTest {
    private var clicks = 0

    private fun ComposeUiTest.show(
        selected: ProjectId? = QUAYSIDE.id,
        projects: List<Project> = NewAgentWorld.PROJECTS,
    ): MissionControlController {
        var built: MissionControlController? = null
        val sessions = NewAgentWorld.sessions()
        setContent {
            AmTestTheme {
                val scope = rememberCoroutineScope()
                val controller =
                    remember {
                        MissionControlController(
                            SessionsApi(monitor = sessions, commands = sessions),
                            FakeProjectsApi(initial = projects),
                            MutableStateFlow(selected),
                            TestClock(),
                            scope,
                            NoOpLogger,
                            tick = 1.hours,
                        ).also { built = it }
                    }
                MissionControl(controller, onNewAgent = { clicks++ })
            }
        }
        waitUntil { built != null }
        return checkNotNull(built)
    }

    @Test
    fun `the project page has the button with its shortcut`() =
        runComposeUiTest {
            show()

            await(MissionControlTags.NEW_AGENT)
            onNode(hasTestTag(MissionControlTags.NEW_AGENT) and hasText("Nowy agent", substring = true)).assertExists()
            onNode(hasTestTag(MissionControlTags.NEW_AGENT) and hasText("⌘N", substring = true)).assertExists()
        }

    @Test
    fun `a click calls the callback once`() =
        runComposeUiTest {
            show()

            await(MissionControlTags.NEW_AGENT).performClick()
            waitForIdle()

            clicks shouldBe 1
        }

    @Test
    fun `NFR-6 the button never takes the focus`() =
        runComposeUiTest {
            show()

            await(MissionControlTags.NEW_AGENT).performClick()
            waitForIdle()

            onNodeWithTag(MissionControlTags.NEW_AGENT).assertIsNotFocused()
        }

    @Test
    fun `a global view has no button`() =
        runComposeUiTest {
            val controller = show()

            runOnIdle { controller.onIntent(MissionControlIntent.ShowView(GlobalView.WORKING)) }
            waitForIdle()

            onNodeWithTag(MissionControlTags.NEW_AGENT).assertDoesNotExist()
        }

    @Test
    fun `no project selected has no button`() =
        runComposeUiTest {
            show(selected = null)

            await(MissionControlTags.EMPTY)

            onNodeWithTag(MissionControlTags.NEW_AGENT).assertDoesNotExist()
        }

    @Test
    fun `no projects has no button`() =
        runComposeUiTest {
            show(selected = null, projects = emptyList())

            await(MissionControlTags.EMPTY)

            onNodeWithTag(MissionControlTags.NEW_AGENT).assertDoesNotExist()
        }
}
