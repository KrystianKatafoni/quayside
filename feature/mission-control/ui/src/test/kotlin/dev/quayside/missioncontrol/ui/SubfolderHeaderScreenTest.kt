package dev.quayside.missioncontrol.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.AmTestTheme
import dev.quayside.core.domain.NoOpLogger
import dev.quayside.core.domain.SessionId
import dev.quayside.core.testing.T0
import dev.quayside.core.testing.TestClock
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.ProjectFocus
import dev.quayside.projects.api.SubfolderName
import dev.quayside.projects.api.aProject
import dev.quayside.projects.api.subfolderNames
import dev.quayside.sessions.api.FakeSessions
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.SessionsApi
import dev.quayside.sessions.api.SessionsWorld
import dev.quayside.sessions.api.WorldProject
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

/** The header of a subfolder page (FR-P7, AM-306): "plant-puls / soft", the path of the subfolder, the count. */
@OptIn(ExperimentalTestApi::class)
internal class SubfolderHeaderScreenTest {
    private val plantDir = Path.of("/Users/test/projects/plant-puls")
    private val plant =
        aProject(id = "plant", dir = plantDir, displayName = "plant-puls", subfolders = subfolderNames("soft"))

    private fun session(
        id: String,
        cwd: Path,
    ) = WorldSession(SessionId(id), cwd, SessionState.Working, name = "name-$id", startedAt = T0)

    private fun fakeSessions(): FakeSessions =
        FakeSessions(
            SessionsWorld(
                projects = listOf(WorldProject(plant.id, plant.dir)),
                sessions =
                    listOf(
                        session("in-soft", plantDir.resolve("soft/api")),
                        session("also-soft", plantDir.resolve("soft")),
                        session("in-root", plantDir),
                    ),
            ),
            TestClock(T0),
        )

    private fun ComposeUiTest.show(focus: ProjectFocus) {
        val fake = fakeSessions()
        setContent {
            AmTestTheme {
                val scope = rememberCoroutineScope()
                val controller =
                    remember {
                        MissionControlController(
                            SessionsApi(monitor = fake, commands = fake),
                            FakeProjectsApi(initial = listOf(plant)),
                            MutableStateFlow<ProjectFocus?>(focus),
                            TestClock(T0.plusSeconds(60)),
                            scope,
                            NoOpLogger,
                            tick = 1.hours,
                        )
                    }
                MissionControl(controller)
            }
        }
    }

    @Test
    fun `header shows project and subfolder, the path of the subfolder and its count`() =
        runComposeUiTest {
            show(ProjectFocus(plant.id, SubfolderName("soft")))

            awaitTag(MissionControlTags.HEADER)
            textUnder(MissionControlTags.HEADER, "plant-puls / soft").assertExists()
            textUnder(MissionControlTags.HEADER_PATH, plantDir.resolve("soft").toString()).assertExists()
            textUnder(MissionControlTags.HEADER_COUNT, mcText("header.sessions", "2")).assertExists()
        }

    @Test
    fun `header of the project row keeps the project name, its path and the whole count`() =
        runComposeUiTest {
            show(ProjectFocus(plant.id))

            awaitTag(MissionControlTags.HEADER)
            textUnder(MissionControlTags.HEADER_PATH, plantDir.toString()).assertExists()
            textUnder(MissionControlTags.HEADER_COUNT, mcText("header.sessions", "3")).assertExists()
        }

    @Test
    fun `rows of the subfolder page are only the sessions of the subfolder`() =
        runComposeUiTest {
            show(ProjectFocus(plant.id, SubfolderName("soft")))

            awaitTag(MissionControlTags.row(SessionId("in-soft")))
            awaitTag(MissionControlTags.row(SessionId("also-soft")))
            hasTag(MissionControlTags.row(SessionId("in-root"))) shouldBe false
        }
}
