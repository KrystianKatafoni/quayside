@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.SessionId
import dev.quayside.missioncontrol.ui.NewAgentWorld.OTHER
import dev.quayside.missioncontrol.ui.NewAgentWorld.PROJECTS
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.sessions.api.SessionError
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The running start, the reopened dialog and the logs of the "Nowy agent" dialog (FR-S3, ENG-C8, AM-608 rule 4):
 * one start at a time, no cancel while it runs, a fresh form after every close, no input in the logs.
 */
internal class NewAgentControllerBusyTest : NewAgentControllerFixture() {
    @Test
    fun `success logs the session and the project and nothing else`() =
        runTest {
            val controller = opened()
            controller.onEvent(
                NewAgentEvent.Edit(
                    NewAgentForm(
                        QUAYSIDE.id,
                        prompt = NewAgentWorld.SECRET_PROMPT,
                        name = NewAgentWorld.SECRET_NAME,
                        agent = NewAgentWorld.SECRET_AGENT,
                    ),
                ),
            )

            submit(controller)

            val info = log.entriesAt(LogLevel.INFO).joinToString(" ") { it.fields.values.joinToString(" ") }
            info shouldContain first.value
            info shouldContain QUAYSIDE.id.value
        }

    @Test
    fun `failure logs a warning with the category and not the input`() =
        runTest {
            val controller = opened()
            controller.onEvent(NewAgentEvent.Edit(NewAgentForm(QUAYSIDE.id, prompt = NewAgentWorld.SECRET_PROMPT)))
            fake.failNext(refused)

            submit(controller)

            val fields = log.entriesAt(LogLevel.WARN).joinToString(" ") { it.fields.values.joinToString(" ") }
            fields shouldContain "Runtime"
            fields shouldContain "COMMAND_FAILED"
        }

    @Test
    fun `second submit while busy is ignored`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val commands = RecordingCommands(fake, gate)
            val controller = opened(commands)
            controller.edit()
            submit(controller)
            controller.shown().busy shouldBe true

            submit(controller)
            gate.complete(Unit)
            runCurrent()

            commands.specs.size shouldBe 1
            started.size shouldBe 1
        }

    @Test
    fun `two submits before the start runs start once`() =
        runTest {
            val commands = RecordingCommands(fake)
            val controller = opened(commands)
            controller.edit()

            controller.onEvent(NewAgentEvent.Submit)
            controller.onEvent(NewAgentEvent.Submit)
            runCurrent()

            commands.specs.size shouldBe 1
            started.size shouldBe 1
        }

    @Test
    fun `busy is shown while the start runs and the form stays`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val controller = opened(RecordingCommands(fake, gate))
            controller.edit()
            val form = controller.shown().form

            submit(controller)

            controller.shown() shouldBe NewAgentState(form, PROJECTS, busy = true)
            gate.complete(Unit)
            runCurrent()
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `edit while busy changes nothing`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val controller = opened(RecordingCommands(fake, gate))
            controller.edit()
            submit(controller)
            val before = controller.shown()

            controller.onEvent(NewAgentEvent.Edit(before.form.copy(prompt = "changed", model = ModelChoice.HAIKU)))

            controller.shown() shouldBe before
            gate.complete(Unit)
            runCurrent()
            started.size shouldBe 1
        }

    @Test
    fun `cancel while busy is ignored`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val controller = opened(RecordingCommands(fake, gate))
            controller.edit()
            submit(controller)

            controller.onEvent(NewAgentEvent.Cancel)
            runCurrent()

            controller.shown().busy shouldBe true
            started.shouldBeEmpty()
            gate.complete(Unit)
            runCurrent()
            started.size shouldBe 1
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `open while busy changes nothing`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val controller = opened(RecordingCommands(fake, gate))
            controller.edit()
            submit(controller)

            controller.open(OTHER.id)

            controller.shown().form.project shouldBe QUAYSIDE.id
            gate.complete(Unit)
            runCurrent()
            started shouldBe listOf(QUAYSIDE.id to first)
        }

    @Test
    fun `cancel closes without a start`() =
        runTest {
            val commands = RecordingCommands(fake)
            val controller = opened(commands)
            controller.edit()

            controller.onEvent(NewAgentEvent.Cancel)
            runCurrent()

            controller.state.value.shouldBeNull()
            controller.isOpen.value shouldBe false
            commands.specs.shouldBeEmpty()
            started.shouldBeEmpty()
        }

    @Test
    fun `a reopened dialog is fresh after a cancel`() =
        runTest {
            val controller = opened()
            controller.edit(prompt = "old", name = "old-name")
            controller.onEvent(NewAgentEvent.Cancel)

            controller.open(OTHER.id)

            controller.state.value shouldBe NewAgentState(NewAgentForm(OTHER.id), PROJECTS)
        }

    @Test
    fun `a reopened dialog is fresh after a failure and a cancel`() =
        runTest {
            val controller = opened()
            controller.edit()
            fake.failNext(refused)
            submit(controller)
            controller.onEvent(NewAgentEvent.Cancel)

            controller.open(QUAYSIDE.id)

            controller.state.value shouldBe NewAgentState(NewAgentForm(QUAYSIDE.id), PROJECTS)
        }

    @Test
    fun `a reopened dialog is fresh after a success and a second start works`() =
        runTest {
            val commands = RecordingCommands(fake)
            val controller = opened(commands)
            controller.edit()
            submit(controller)

            controller.open(QUAYSIDE.id)

            controller.state.value shouldBe NewAgentState(NewAgentForm(QUAYSIDE.id), PROJECTS)
            controller.edit(prompt = "second")
            submit(controller)
            started shouldBe listOf(QUAYSIDE.id to first, QUAYSIDE.id to SessionId("00000002"))
        }

    @Test
    fun `logs never contain the prompt, the name or the agent`() =
        runTest {
            val controller = opened()
            val secret =
                NewAgentForm(
                    QUAYSIDE.id,
                    prompt = NewAgentWorld.SECRET_PROMPT,
                    name = NewAgentWorld.SECRET_NAME,
                    agent = NewAgentWorld.SECRET_AGENT,
                )
            controller.onEvent(NewAgentEvent.Edit(secret.copy(agent = "bad agent")))
            submit(controller)
            controller.onEvent(NewAgentEvent.Edit(secret))
            fake.failNext(refused)
            submit(controller)
            fake.failNext(SessionError.UnknownProject(QUAYSIDE.id))
            controller.onEvent(NewAgentEvent.Edit(secret.copy(project = QUAYSIDE.id)))
            submit(controller)
            controller.onEvent(NewAgentEvent.Edit(secret.copy(project = QUAYSIDE.id)))
            submit(controller)

            val dump = log.dump()
            listOf(NewAgentWorld.SECRET_PROMPT, NewAgentWorld.SECRET_NAME, NewAgentWorld.SECRET_AGENT).forEach {
                dump.shouldNotContain(it)
            }
        }

    @Test
    fun `the failure text does not quote the input`() =
        runTest {
            val controller = opened()
            controller.onEvent(NewAgentEvent.Edit(NewAgentForm(QUAYSIDE.id, prompt = NewAgentWorld.SECRET_PROMPT)))
            fake.failNext(refused)

            submit(controller)

            val reason = (controller.shown().problem as NewAgentProblem.StartFailed).reason
            reason.args shouldBe emptyList()
            MissionControlStrings.format(reason).shouldNotContain(NewAgentWorld.SECRET_PROMPT)
        }
}
