@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.missioncontrol.ui.GlobalViewWorld.Companion.OTHER
import dev.quayside.missioncontrol.ui.GlobalViewWorld.Companion.PROJECT
import dev.quayside.missioncontrol.ui.GlobalViewWorld.Companion.session
import dev.quayside.missioncontrol.ui.GlobalViewWorld.Companion.working
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * `SelectSession`: `app` selects the session it has just started (AM-608, rule 6). It sets the project, the filter
 * "Wszystkie" and the session at once; the sidebar follows later with the same project.
 */
internal class SelectSessionControllerTest {
    @Test
    fun `select session shows its project with filter all`() =
        runTest {
            val world = GlobalViewWorld(working())
            val controller = with(world) { start() }
            controller.onIntent(MissionControlIntent.SelectFilter(SessionFilter.FINISHED))

            controller.onIntent(MissionControlIntent.SelectSession(PROJECT.id, SessionId("mine")))
            runCurrent()

            val page = controller.projectPage()
            page.header.name shouldBe "Quayside"
            page.filter shouldBe SessionFilter.ALL
            page.details?.id shouldBe SessionId("mine")
            page.rows.single { it.selected }.id shouldBe SessionId("mine")
        }

    @Test
    fun `select session in another project switches to it before the sidebar follows`() =
        runTest {
            val world = GlobalViewWorld(working())
            val controller = with(world) { start() }

            controller.onIntent(MissionControlIntent.SelectSession(OTHER.id, SessionId("theirs")))
            runCurrent()
            // The sidebar answers ProjectsIntent.Select with the same project; the session stays selected.
            world.selected.value = OTHER.id
            runCurrent()

            val page = controller.projectPage()
            page.header.name shouldBe "Other"
            page.details?.id shouldBe SessionId("theirs")
            page.rows.map { it.id.value } shouldBe listOf("theirs")
        }

    @Test
    fun `select session leaves a global view`() =
        runTest {
            val world = GlobalViewWorld(working(), selected = null)
            val controller = with(world) { start() }
            controller.onIntent(MissionControlIntent.ShowView(GlobalView.WORKING))
            runCurrent()
            controller.view.value shouldBe GlobalView.WORKING

            controller.onIntent(MissionControlIntent.SelectSession(PROJECT.id, SessionId("mine")))
            runCurrent()

            controller.view.value.shouldBeNull()
            controller.projectPage().details?.id shouldBe SessionId("mine")
        }

    @Test
    fun `select session works from the page without a project`() =
        runTest {
            val world = GlobalViewWorld(working(), selected = null)
            val controller = with(world) { start() }

            controller.onIntent(MissionControlIntent.SelectSession(PROJECT.id, SessionId("mine")))
            runCurrent()

            controller.projectPage().details?.id shouldBe SessionId("mine")
        }

    @Test
    fun `the palette actions follow the selected session`() =
        runTest {
            val world = GlobalViewWorld(listOf(session("done", 1, SessionState.Done), session("busy", 2)))
            val controller = with(world) { start() }

            controller.onIntent(MissionControlIntent.SelectSession(PROJECT.id, SessionId("busy")))
            runCurrent()

            controller.actions.value.canStop shouldBe true
            controller.actions.value.canRespawn shouldBe false
        }

    @Test
    fun `select session is ignored while a dialog is open`() =
        runTest {
            val world =
                GlobalViewWorld(
                    listOf(session("finished", 1, SessionState.Done), session("theirs", 2, cwd = OTHER.dir)),
                )
            val controller = with(world) { start() }
            controller.onEvent(MissionControlEvent.SelectSession(SessionId("finished")))
            controller.onIntent(MissionControlIntent.RemoveSelected)
            runCurrent()
            val dialog = controller.state.value.dialog

            controller.onIntent(MissionControlIntent.SelectSession(OTHER.id, SessionId("theirs")))
            runCurrent()

            controller.state.value.dialog shouldBe dialog
            controller.projectPage().header.name shouldBe "Quayside"
            controller.projectPage().details?.id shouldBe SessionId("finished")
        }
}
