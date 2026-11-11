@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.SessionId
import dev.quayside.sessions.api.RuntimeProblem
import dev.quayside.sessions.api.SessionError
import dev.quayside.sessions.api.SessionOrigin
import dev.quayside.sessions.api.SubfolderProblem
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The list of places in Mission Control over `FakeSessions`, which passes the same `PlaceContract` as the real system
 * (FR-P9, AM-314): open from the details or the row menu (`Run(ASSIGN_PLACE)`), pick one line (`PickPlace`). The group
 * is `plant-puls` with `bob, Carl, Anna`; the sessions run in `bob`, so the working directory and the place differ.
 * The list holds positions (`sub.2`), never names, so the events carry no subfolder name.
 */
internal class PlaceFlowTest : PlaceFlowFixture() {
    @Test
    fun `opening the list holds the choices and the current one`() =
        runTest {
            val controller = start(world())

            val dialog = open(controller, one)

            assertSoftly {
                dialog.id shouldBe one
                dialog.choices shouldBe PlaceChoices.of(group)
                dialog.current shouldBe "auto"
            }
        }

    @Test
    fun `picking a choice assigns once`() =
        runTest {
            val fake = world()
            val controller = start(fake)
            open(controller, one)

            event(controller, MissionControlEvent.PickPlace(carl))
            event(controller, MissionControlEvent.PickPlace(carl))

            fake.calls shouldBe listOf("assignPlace ${one.value}")
            assertSoftly {
                fake.dirOf(one) shouldBe OVERVIEW_DIR.resolve("Carl")
                controller.state.value.dialog
                    .shouldBeNull()
                controller.details()?.failure.shouldBeNull()
                controller.details()?.busy.shouldBeNull()
            }
        }

    @Test
    fun `picking automatic clears the place`() =
        runTest {
            val fake = world()
            val controller = start(fake)
            open(controller, one)
            event(controller, MissionControlEvent.PickPlace(carl))
            open(controller, one).current shouldBe carl

            event(controller, MissionControlEvent.PickPlace("auto"))

            fake.dirOf(one).shouldBeNull()
        }

    @Test
    fun `picking the current choice calls nothing`() =
        runTest {
            val fake = world()
            val controller = start(fake)
            open(controller, one)

            event(controller, MissionControlEvent.PickPlace("auto"))

            fake.calls.shouldBeEmpty()
            controller.state.value.dialog
                .shouldBeNull()
        }

    @Test
    fun `picking something that is not in the list calls nothing`() =
        runTest {
            val fake = world()
            val controller = start(fake)
            open(controller, one)

            event(controller, MissionControlEvent.PickPlace("sub.9"))
            event(controller, MissionControlEvent.PickPlace("Carl"))

            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `a pick without an open list calls nothing`() =
        runTest {
            val fake = world()
            val controller = start(fake)
            event(controller, MissionControlEvent.SelectSession(one))

            event(controller, MissionControlEvent.PickPlace(carl))

            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `dismiss closes the list and calls nothing`() =
        runTest {
            val fake = world()
            val controller = start(fake)
            open(controller, one)

            event(controller, MissionControlEvent.Dismiss)

            controller.state.value.dialog
                .shouldBeNull()
            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `failed assign keeps the place and shows a fixed text without the name`() =
        runTest {
            val fake = world()
            fake.failNext(SessionError.Runtime(RuntimeProblem.COMMAND_FAILED))
            val controller = start(fake)
            open(controller, one)

            event(controller, MissionControlEvent.PickPlace(carl))

            assertSoftly {
                controller.details()?.failure shouldBe
                    Failure(one, SessionAction.ASSIGN_PLACE, UiText("action.reason.runtime.COMMAND_FAILED"))
                fake.dirOf(one).shouldBeNull()
                controller.state.value.dialog
                    .shouldBeNull()
            }
            val warning = log.entries.single { it.level == LogLevel.WARN }
            warning.fields["reason"] shouldBe "runtime.COMMAND_FAILED"
            val logged = log.entries.toString()
            listOf("Carl", "bob", "name-${one.value}", OVERVIEW_DIR.toString()).forEach { logged shouldNotContain it }
        }

    @Test
    fun `a rejected subfolder shows a fixed text`() =
        runTest {
            val fake = world()
            fake.failNext(SessionError.SubfolderUnavailable(group.id, SubfolderProblem.NOT_IN_PROJECT))
            val controller = start(fake)
            open(controller, one)

            event(controller, MissionControlEvent.PickPlace(carl))

            controller.details()?.failure?.reason shouldBe UiText("action.reason.other")
            log.entries
                .single { it.level == LogLevel.WARN }
                .fields["reason"] shouldBe "subfolderUnavailable"
        }

    @Test
    fun `the details offer the action for a group session in any state and origin`() =
        runTest {
            val controller = start(world())

            event(controller, MissionControlEvent.SelectSession(one))
            controller.details()?.actions?.contains(SessionAction.ASSIGN_PLACE) shouldBe true
            event(controller, MissionControlEvent.SelectSession(two))
            controller.details()?.actions?.contains(SessionAction.ASSIGN_PLACE) shouldBe true
            event(controller, MissionControlEvent.SelectSession(disk))
            controller.details()?.actions?.contains(SessionAction.ASSIGN_PLACE) shouldBe true
            controller.details()?.fromDisk shouldBe true
        }

    @Test
    fun `AssignPlace is ignored for a session outside a group`() =
        runTest {
            val fake = world()
            val controller = start(fake, shown = plain)
            event(controller, MissionControlEvent.SelectSession(SessionId("lone0001")))

            event(controller, MissionControlEvent.Run(SessionAction.ASSIGN_PLACE))
            event(controller, MissionControlEvent.PickPlace(carl))

            assertSoftly {
                controller.state.value.dialog
                    .shouldBeNull()
                controller.details()?.actions?.contains(SessionAction.ASSIGN_PLACE) shouldBe false
                fake.calls.shouldBeEmpty()
            }
        }

    @Test
    fun `AssignPlace is ignored without a selected session`() =
        runTest {
            val fake = world()
            val controller = start(fake)

            event(controller, MissionControlEvent.Run(SessionAction.ASSIGN_PLACE))

            controller.state.value.dialog
                .shouldBeNull()
            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `AssignPlace keeps an open confirmation of removing`() =
        runTest {
            val fake = world()
            val controller = start(fake)
            event(controller, MissionControlEvent.SelectSession(two))
            event(controller, MissionControlEvent.Run(SessionAction.REMOVE))
            val confirmation = SessionDialog.ConfirmRemove(two, "name-${two.value}")
            controller.state.value.dialog shouldBe confirmation

            event(controller, MissionControlEvent.Run(SessionAction.ASSIGN_PLACE))

            controller.state.value.dialog shouldBe confirmation
            fake.calls.shouldBeEmpty()
        }

    @Test
    fun `assigning works for a disk session`() =
        runTest {
            val fake = world()
            val controller = start(fake)
            open(controller, disk).current shouldBe "auto"

            event(controller, MissionControlEvent.PickPlace("root"))

            assertSoftly {
                fake.dirOf(disk) shouldBe OVERVIEW_DIR
                fake.snapshot.value.sessions
                    .single { it.id == disk }
                    .origin shouldBe SessionOrigin.DISK
            }
        }
}
