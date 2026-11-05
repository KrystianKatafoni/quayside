package dev.quayside.projects.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.quayside.core.designsystem.toColor
import dev.quayside.core.designtokens.SampleTokens
import dev.quayside.core.testing.aSessionId
import dev.quayside.projects.api.A_PROJECT_DIR
import dev.quayside.projects.api.FakeProjectsApi
import dev.quayside.projects.api.InspectionWorld
import dev.quayside.projects.api.aProject
import dev.quayside.sessions.api.SessionState
import dev.quayside.sessions.api.WorldSession
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.file.Path

/** The collapsed project bar of terminal mode (plan AM-809, mockup B). */
@OptIn(ExperimentalTestApi::class)
internal class ProjectsRailTest {
    private val busy = aProject(id = "project0001", dir = A_PROJECT_DIR, displayName = "Busy")
    private val quiet = aProject(id = "project0002", dir = Path.of("/Users/test/projects/quiet"), displayName = "Quiet")
    private val colors = SampleTokens.colors()
    private var clicks = 0

    private fun world(inspection: InspectionWorld = InspectionWorld()): ControllerWorld {
        val running =
            listOf(
                WorldSession(aSessionId("w1"), A_PROJECT_DIR, SessionState.AwaitingInput(null)),
                WorldSession(aSessionId("w2"), A_PROJECT_DIR, SessionState.AwaitingInput(null)),
                WorldSession(aSessionId("p1"), A_PROJECT_DIR, SessionState.Working),
            )
        return ControllerWorld(
            projects = FakeProjectsApi(initial = listOf(busy, quiet)),
            inspection = inspection,
            running = running,
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showRail(world: ControllerWorld): ProjectsController =
        showProjects(world) { ProjectsRail(it, onProjectClick = { clicks++ }) }

    @Test
    fun `rail shows one labelled icon button per project`() =
        runComposeUiTest {
            showRail(world())

            // The busy project is announced with its counts (the snapshot comes later), the quiet one by name
            waitUntil {
                runCatching {
                    awaitTag(ProjectsTags.railButton(busy.id))
                        .assertContentDescriptionEquals("Busy, 2 czekają, 1 pracuje")
                }.isSuccess
            }
            awaitTag(ProjectsTags.railButton(quiet.id)).assertContentDescriptionEquals("Quiet")
            onAllNodes(
                androidx.compose.ui.test
                    .hasTestTag(ProjectsTags.RAIL),
            ).assertCountEquals(1)
        }

    @Test
    fun `rail click selects the project and calls onProjectClick`() =
        runComposeUiTest {
            val controller = showRail(world())

            awaitTag(ProjectsTags.railButton(quiet.id)).performClick()
            waitForIdle()

            controller.selection.value shouldBe quiet.id
            clicks shouldBe 1
        }

    @Test
    fun `rail marks the selected project`() =
        runComposeUiTest {
            // Given the quiet project selected
            val controller = showRail(world())
            awaitTag(ProjectsTags.railButton(quiet.id)).performClick()
            waitForIdle()
            controller.selection.value shouldBe quiet.id

            // Then its button is filled with sel and the other one is not
            val sel =
                colors.surfaces.sel
                    .toColor()
                    .toArgb()
            awaitTag(ProjectsTags.railButton(quiet.id)).argbColors() shouldContain sel
            awaitTag(ProjectsTags.railButton(busy.id)).argbColors() shouldNotContain sel
        }

    @Test
    fun `rail badge shows the waiting count`() =
        runComposeUiTest {
            showRail(world())
            waitUntil {
                runCatching {
                    awaitTag(
                        ProjectsTags.railButton(busy.id),
                    ).assertContentDescriptionEquals("Busy, 2 czekają, 1 pracuje")
                }.isSuccess
            }

            // The counter badge is a warn circle with warnInk digits; the quiet project has none
            val badge =
                colors.status.warnInk
                    .toColor()
                    .toArgb()
            awaitTag(ProjectsTags.railButton(busy.id)).argbColors() shouldContain badge
            awaitTag(ProjectsTags.railButton(quiet.id)).argbColors() shouldNotContain badge
        }

    @Test
    fun `rail add opens the directory picker`() =
        runComposeUiTest {
            val world = world()
            world.picker.willCancel()
            showRail(world)

            awaitTag(ProjectsTags.RAIL_ADD).performClick()

            waitUntil { world.picker.picks == 1 }
        }

    @Test
    fun `rail buttons never take focus`() =
        runComposeUiTest {
            showRail(world())
            val button = awaitTag(ProjectsTags.railButton(busy.id))

            // When the user clicks a button and moves focus with Tab
            button.performClick()
            onRoot().performKeyInput {
                keyDown(Key.Tab)
                keyUp(Key.Tab)
            }
            waitForIdle()

            // Then nothing in the rail has focus, "+" included
            button.assertIsNotFocused()
            awaitTag(ProjectsTags.RAIL_ADD).assertIsNotFocused()
            onAllNodes(isFocused()).assertCountEquals(0)
        }

    @Test
    fun `rail shows the projects dialog`() =
        runComposeUiTest {
            // Given the chooser answers with a folder
            val world = world()
            world.picker.willPick(Path.of("/Users/test/projects/new"))
            showRail(world)

            // When the user adds a project from the rail
            awaitTag(ProjectsTags.RAIL_ADD).performClick()

            // Then the add dialog is drawn although no sidebar is shown
            awaitTag(ProjectsTags.CONFIRM_ADD)
        }
}
