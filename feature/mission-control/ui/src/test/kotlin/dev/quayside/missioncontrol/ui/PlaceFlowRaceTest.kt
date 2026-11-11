@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The list of places while a command runs and the user moves on (FR-P9, AM-314): a late result or failure of the first
 * session never touches the second one, and a list opened for another session holds that session's own current choice.
 */
internal class PlaceFlowRaceTest : PlaceFlowFixture() {
    @Test
    fun `a list opened for another session holds that session and its current choice`() =
        runTest {
            val fake = world()
            val controller = start(fake)
            open(controller, two)
            event(controller, MissionControlEvent.PickPlace(carl))
            open(controller, one)
            event(controller, MissionControlEvent.Dismiss)

            val reopened = open(controller, two)

            assertSoftly {
                reopened.id shouldBe two
                reopened.current shouldBe carl
            }
        }

    @Test
    fun `a late result for the first session does not touch the list of the second`() =
        runTest {
            val fake = world()
            val gated = GatedCommands(fake)
            val controller = start(fake, gated)
            open(controller, one)
            event(controller, MissionControlEvent.PickPlace(carl))
            gated.started shouldBe listOf("assignPlace ${one.value}")
            // busy: the second list cannot open and the first assign is not repeated
            event(controller, MissionControlEvent.SelectSession(two))
            event(controller, MissionControlEvent.Run(SessionAction.ASSIGN_PLACE))
            controller.state.value.dialog
                .shouldBeNull()

            gated.gate.complete(Unit)
            runCurrent()

            assertSoftly {
                fake.dirOf(one) shouldBe OVERVIEW_DIR.resolve("Carl")
                fake.dirOf(two).shouldBeNull()
                controller.details()?.id shouldBe two
                controller.details()?.failure.shouldBeNull()
                controller.state.value.dialog
                    .shouldBeNull()
            }
            open(controller, two).id shouldBe two
        }

    @Test
    fun `a failure for the first session does not show on the selected second one`() =
        runTest {
            val fake = world()
            fake.failNext(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
            val gated = GatedCommands(fake)
            val controller = start(fake, gated)
            open(controller, one)
            event(controller, MissionControlEvent.PickPlace(carl))
            event(controller, MissionControlEvent.SelectSession(two))

            gated.gate.complete(Unit)
            runCurrent()

            controller.details()?.id shouldBe two
            controller.details()?.failure.shouldBeNull()
        }

    @Test
    fun `no other action is offered while assign runs`() =
        runTest {
            val fake = world()
            val gated = GatedCommands(fake)
            val controller = start(fake, gated)
            open(controller, one)

            event(controller, MissionControlEvent.PickPlace(carl))

            assertSoftly {
                controller.details()?.busy shouldBe SessionAction.ASSIGN_PLACE
                controller.details()?.actions shouldBe emptySet()
                controller.actions.value.canStop shouldBe false
            }
            event(controller, MissionControlEvent.Run(SessionAction.ASSIGN_PLACE))
            gated.started shouldBe listOf("assignPlace ${one.value}")
        }
}
