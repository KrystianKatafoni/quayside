@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.SessionId
import dev.quayside.missioncontrol.ui.GlobalViewWorld.Companion.ELSEWHERE
import dev.quayside.missioncontrol.ui.GlobalViewWorld.Companion.OTHER
import dev.quayside.missioncontrol.ui.GlobalViewWorld.Companion.session
import dev.quayside.missioncontrol.ui.GlobalViewWorld.Companion.working
import dev.quayside.sessions.api.SessionState
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * j, k and the session actions in a global view (FR-K2, FR-S4, AM-407, decision 4). The rows of a view are newest
 * first: with `working()` they are nowhere, theirs, mine.
 */
internal class GlobalViewActionsTest {
    private fun TestScope.shown(
        world: GlobalViewWorld,
        view: GlobalView,
    ): MissionControlController {
        val controller = with(world) { start() }
        controller.onIntent(MissionControlIntent.ShowView(view))
        runCurrent()
        return controller
    }

    private fun TestScope.send(
        controller: MissionControlController,
        vararg intents: MissionControlIntent,
    ) {
        intents.forEach {
            controller.onIntent(it)
            runCurrent()
        }
    }

    @Test
    fun `j and k move over the rows of the global view`() =
        runTest {
            val controller = shown(GlobalViewWorld(working()), GlobalView.WORKING)

            send(controller, MissionControlIntent.NextSession)
            controller.globalSelected() shouldBe "nowhere"

            send(controller, MissionControlIntent.NextSession, MissionControlIntent.NextSession)
            controller.globalSelected() shouldBe "mine"
            controller.global().details?.id shouldBe SessionId("mine")

            send(controller, MissionControlIntent.NextSession)
            controller.globalSelected() shouldBe "mine"

            send(controller, MissionControlIntent.PreviousSession)
            controller.globalSelected() shouldBe "theirs"
        }

    @Test
    fun `k without a selection selects the last row of the global view`() =
        runTest {
            val controller = shown(GlobalViewWorld(working()), GlobalView.WORKING)

            send(controller, MissionControlIntent.PreviousSession)

            controller.globalSelected() shouldBe "mine"
        }

    @Test
    fun `j right after a new snapshot counts the fresh rows`() =
        runTest {
            val world = GlobalViewWorld(working())
            val controller = shown(world, GlobalView.WORKING)

            // The new session is the newest, so it is the first row. `state` has not seen it yet when j arrives.
            world.fake.appear(session("late", 9, cwd = OTHER.dir))
            controller.onIntent(MissionControlIntent.NextSession)
            runCurrent()

            controller.globalIds().first() shouldBe "late"
            controller.globalSelected() shouldBe "late"
        }

    @Test
    fun `stop in the working view stops a session of another project`() =
        runTest {
            val world = GlobalViewWorld(working())
            val controller = shown(world, GlobalView.WORKING)
            controller.onEvent(MissionControlEvent.SelectSession(SessionId("theirs")))
            runCurrent()
            controller.global().details?.actions shouldBe setOf(SessionAction.STOP)

            send(controller, MissionControlIntent.StopSelected)

            world.fake.calls shouldBe listOf("stop theirs")
            // The stopped session leaves the view and the panel asks to pick a session.
            controller.globalIds() shouldBe listOf("nowhere", "mine")
            controller.global().details.shouldBeNull()
            controller.global().rows.none { it.selected } shouldBe true
        }

    @Test
    fun `respawn in the failed view respawns a session without a project`() =
        runTest {
            val world = GlobalViewWorld(listOf(session("broken", 1, SessionState.Failed, cwd = ELSEWHERE)))
            val controller = shown(world, GlobalView.FAILED)
            controller.onEvent(MissionControlEvent.SelectSession(SessionId("broken")))
            runCurrent()
            controller.global().details?.actions shouldBe setOf(SessionAction.RESPAWN, SessionAction.REMOVE)
            controller.actions.value.canRespawn shouldBe true

            send(controller, MissionControlIntent.RespawnSelected)

            world.fake.calls shouldBe listOf("respawn broken")
            controller.globalIds() shouldBe emptyList()
            controller.global().details.shouldBeNull()
        }

    @Test
    fun `remove in a global view asks to confirm for a session of another project`() =
        runTest {
            val world = GlobalViewWorld(listOf(session("broken", 1, SessionState.Failed, cwd = OTHER.dir)))
            val controller = shown(world, GlobalView.FAILED)
            controller.onEvent(MissionControlEvent.SelectSession(SessionId("broken")))
            runCurrent()

            send(controller, MissionControlIntent.RemoveSelected)

            controller.state.value.dialog shouldBe SessionDialog.ConfirmRemove(SessionId("broken"), "name-broken")
            world.fake.calls shouldBe emptyList()
        }
}
