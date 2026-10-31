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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * `ShowView`, the `view` stream and how the sidebar selection meets a global view (FR-S9, AM-407, decisions 1, 2
 * and 5). Time moves only by `advanceTimeBy` + `runCurrent`, never `advanceUntilIdle`.
 */
internal class GlobalViewControllerTest {
    private fun TestScope.show(
        controller: MissionControlController,
        view: GlobalView,
    ) {
        controller.onIntent(MissionControlIntent.ShowView(view))
        runCurrent()
    }

    @Test
    fun `show view clears the session and reports the view`() =
        runTest {
            val world = GlobalViewWorld(working())
            val controller = with(world) { start() }
            controller.view.value.shouldBeNull()
            controller.onEvent(MissionControlEvent.SelectSession(SessionId("mine")))
            runCurrent()
            controller.projectPage().details?.id shouldBe SessionId("mine")

            show(controller, GlobalView.WORKING)

            controller.view.value shouldBe GlobalView.WORKING
            controller.global().view shouldBe GlobalView.WORKING
            controller.globalIds() shouldBe listOf("nowhere", "theirs", "mine")
            controller.global().details.shouldBeNull()
            controller.state.value.views
                .single { it.selected }
                .view shouldBe GlobalView.WORKING
        }

    @Test
    fun `a cleared project keeps the global view`() =
        runTest {
            val world = GlobalViewWorld(working())
            val controller = with(world) { start() }
            show(controller, GlobalView.WORKING)

            // The sidebar answers ShowView with no project (ClearSelection).
            world.selected.value = null
            runCurrent()

            controller.view.value shouldBe GlobalView.WORKING
            controller.globalIds() shouldBe listOf("nowhere", "theirs", "mine")
        }

    @Test
    fun `picking a project leaves the global view`() =
        runTest {
            val world = GlobalViewWorld(working())
            val controller = with(world) { start() }
            controller.onIntent(MissionControlIntent.SelectFilter(SessionFilter.FINISHED))
            show(controller, GlobalView.WORKING)
            world.selected.value = null
            runCurrent()

            world.selected.value = OTHER.id
            runCurrent()

            controller.view.value.shouldBeNull()
            val page = controller.projectPage()
            page.header.name shouldBe "Other"
            page.filter shouldBe SessionFilter.ALL
            page.rows.map { it.id.value } shouldBe listOf("theirs")
        }

    @Test
    fun `picking a project while a view is shown leaves the view at once`() =
        runTest {
            val world = GlobalViewWorld(working(), selected = null)
            val controller = with(world) { start() }
            show(controller, GlobalView.FAILED)

            world.selected.value = PROJECT.id
            runCurrent()

            controller.view.value.shouldBeNull()
            controller.projectPage().rows.map { it.id.value } shouldBe listOf("mine")
        }

    @Test
    fun `filter intent is ignored in a global view`() =
        runTest {
            val world = GlobalViewWorld(working() + session("done", 4, SessionState.Done))
            val controller = with(world) { start() }
            show(controller, GlobalView.WORKING)

            controller.onIntent(MissionControlIntent.SelectFilter(SessionFilter.FINISHED))
            runCurrent()

            controller.view.value shouldBe GlobalView.WORKING
            controller.globalIds() shouldBe listOf("nowhere", "theirs", "mine")
        }

    @Test
    fun `show view is ignored while the dialog is open`() =
        runTest {
            val world = GlobalViewWorld(listOf(session("finished", 1, SessionState.Done)))
            val controller = with(world) { start() }
            controller.onEvent(MissionControlEvent.SelectSession(SessionId("finished")))
            controller.onIntent(MissionControlIntent.RemoveSelected)
            runCurrent()
            controller.state.value.dialog shouldBe SessionDialog.ConfirmRemove(SessionId("finished"), "name-finished")

            show(controller, GlobalView.WAITING)

            controller.view.value.shouldBeNull()
            controller.projectPage().rows.map { it.id.value } shouldBe listOf("finished")
        }

    @Test
    fun `ages tick in a global view without a project`() =
        runTest {
            val world = GlobalViewWorld(listOf(session("aging", 0)), selected = null)
            val controller = with(world) { start(tick = 1.seconds) }
            show(controller, GlobalView.WORKING)

            world.clock.advanceBy(2.minutes)
            advanceTimeBy(1.seconds)
            runCurrent()

            controller
                .global()
                .rows
                .single()
                .age shouldBe UiText("age.min", listOf("2"))

            world.clock.advanceBy(1.minutes)
            advanceTimeBy(1.seconds)
            runCurrent()

            controller
                .global()
                .rows
                .single()
                .age shouldBe UiText("age.min", listOf("3"))
        }
}
