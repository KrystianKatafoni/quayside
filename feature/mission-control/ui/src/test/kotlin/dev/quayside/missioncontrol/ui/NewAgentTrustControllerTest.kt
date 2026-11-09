@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.agentruntime.api.TrustError
import dev.quayside.agentruntime.api.WorkspaceTrust
import dev.quayside.core.domain.LogLevel
import dev.quayside.core.domain.Outcome
import dev.quayside.core.domain.ProjectId
import dev.quayside.core.domain.SessionId
import dev.quayside.missioncontrol.ui.NewAgentWorld.OTHER
import dev.quayside.missioncontrol.ui.NewAgentWorld.QUAYSIDE
import dev.quayside.sessions.api.NewSessionSpec
import dev.quayside.sessions.api.SessionCommands
import dev.quayside.sessions.api.SessionError
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [SessionCommands] whose first [refusals] starts fail with [error] (the directory is not trusted); later ones go to
 * [inner]. [attempts] holds every start, refused or not, in order. `FakeSessions.failNext` keeps one failure only.
 */
internal class RefusingCommands(
    private val inner: SessionCommands,
    private var refusals: Int,
    private val error: SessionError,
) : SessionCommands by inner {
    private val recorded = CopyOnWriteArrayList<Pair<ProjectId, NewSessionSpec>>()

    val attempts: List<Pair<ProjectId, NewSessionSpec>> get() = recorded.toList()

    override suspend fun start(
        project: ProjectId,
        spec: NewSessionSpec,
    ): Outcome<SessionId, SessionError> {
        recorded += project to spec
        if (refusals > 0) {
            refusals--
            return Outcome.Err(error)
        }
        return inner.start(project, spec)
    }
}

/** [WorkspaceTrust] that waits for [gate] and counts its calls, so a test sees the "saving" state. */
internal class GatedTrust(
    private val inner: WorkspaceTrust,
    private val gate: CompletableDeferred<Unit>,
) : WorkspaceTrust {
    var calls: Int = 0
        private set

    override suspend fun trust(dir: Path): Outcome<Unit, TrustError> {
        calls++
        gate.await()
        return inner.trust(dir)
    }
}

/**
 * The consent step "Zaufać katalogowi?" of the "Nowy agent" controller (FR-S3, AM-309 rule 4, ADR 0031): asking, the
 * three answers, the second refusal and the logs. `trusting` is `WORKSPACE_NOT_TRUSTED`.
 */
internal class NewAgentTrustControllerTest : NewAgentControllerFixture() {
    private val dir: Path = QUAYSIDE.dir

    private fun refusing(times: Int = 1) = RefusingCommands(fake, times, trusting)

    /** An opened dialog with a filled form whose first start is refused; the consent step is shown. */
    private fun TestScope.asked(
        commands: SessionCommands = refusing(),
        port: WorkspaceTrust = trust,
    ): NewAgentController {
        val controller = opened(commands, port = port)
        controller.edit(prompt = "keep me", name = "keep-name")
        submit(controller)
        return controller
    }

    @Test
    fun `untrusted start asks to trust the project directory and keeps the prompt`() =
        runTest {
            val commands = refusing()
            val controller = asked(commands)

            controller.shown().trust shouldBe TrustStep(dir)
            controller.shown().problem.shouldBeNull()
            controller.shown().busy shouldBe false
            controller.shown().form.prompt shouldBe "keep me"
            controller.shown().form.name shouldBe "keep-name"
            commands.attempts.size shouldBe 1
            trust.calls.shouldBeEmpty()
            controller.isOpen.value shouldBe true
        }

    @Test
    fun `the step asks about the directory of the project of the form`() =
        runTest {
            val controller = opened(refusing(), project = OTHER.id)
            controller.edit()

            submit(controller)

            controller.shown().trust shouldBe TrustStep(OTHER.dir)
        }

    @Test
    fun `decline returns to the form with the manual hint`() =
        runTest {
            val controller = asked()

            controller.onEvent(NewAgentEvent.TrustDecline)

            controller.shown().trust.shouldBeNull()
            controller.shown().problem shouldBe NewAgentProblem.NotTrusted(dir)
            controller.shown().form.prompt shouldBe "keep me"
            controller.shown().busy shouldBe false
            trust.calls.shouldBeEmpty()
            started.shouldBeEmpty()
        }

    @Test
    fun `cancel in the trust step declines and keeps the dialog open`() =
        runTest {
            val controller = asked()

            controller.onEvent(NewAgentEvent.Cancel)

            controller.shown().trust.shouldBeNull()
            controller.shown().problem shouldBe NewAgentProblem.NotTrusted(dir)
            controller.isOpen.value shouldBe true
            trust.calls.shouldBeEmpty()
        }

    @Test
    fun `accept trusts exactly that directory and starts again with the same form`() =
        runTest {
            val commands = refusing()
            val controller = asked(commands)

            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()

            trust.trusted.map(Path::toString) shouldBe listOf(dir.toString())
            commands.attempts.size shouldBe 2
            commands.attempts[1] shouldBe commands.attempts[0]
            started shouldBe listOf(QUAYSIDE.id to first)
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `accept marks the step saving until the write ends`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val controller = asked(port = GatedTrust(trust, gate))

            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()

            controller.shown().trust shouldBe TrustStep(dir, saving = true)
            gate.complete(Unit)
            runCurrent()
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `second accept while saving is ignored`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val gated = GatedTrust(trust, gate)
            val commands = refusing()
            val controller = asked(commands, gated)
            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()

            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()
            gate.complete(Unit)
            runCurrent()

            gated.calls shouldBe 1
            commands.attempts.size shouldBe 2
            started.size shouldBe 1
        }

    @Test
    fun `decline and cancel while saving are ignored and the start still follows`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val commands = refusing()
            val controller = asked(commands, GatedTrust(trust, gate))
            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()

            controller.onEvent(NewAgentEvent.TrustDecline)
            controller.onEvent(NewAgentEvent.Cancel)
            runCurrent()

            controller.shown().trust shouldBe TrustStep(dir, saving = true)
            controller.shown().problem.shouldBeNull()
            gate.complete(Unit)
            runCurrent()
            started.size shouldBe 1
            commands.attempts.size shouldBe 2
        }

    @Test
    fun `accept restarts with the dialog busy and no second submit gets through`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val recording = RecordingCommands(fake, gate)
            val commands = RefusingCommands(recording, 1, trusting)
            val controller = asked(commands)
            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()

            controller.shown().busy shouldBe true
            controller.shown().trust.shouldBeNull()
            submit(controller)
            gate.complete(Unit)
            runCurrent()

            commands.attempts.size shouldBe 2
            started.size shouldBe 1
        }

    @Test
    fun `accept and decline without a trust step do nothing`() =
        runTest {
            val commands = refusing(0)
            val controller = opened(commands)
            controller.edit()

            controller.onEvent(NewAgentEvent.TrustAccept)
            controller.onEvent(NewAgentEvent.TrustDecline)
            runCurrent()

            controller.shown().problem.shouldBeNull()
            controller.shown().trust.shouldBeNull()
            trust.calls.shouldBeEmpty()
            commands.attempts.shouldBeEmpty()
        }

    @Test
    fun `edit in the trust step is ignored`() =
        runTest {
            val controller = asked()
            val before = controller.shown().form

            controller.onEvent(NewAgentEvent.Edit(before.copy(prompt = "changed", project = OTHER.id)))

            controller.shown().form shouldBe before
            controller.shown().trust shouldBe TrustStep(dir)
        }

    @Test
    fun `trust failure shows the manual hint and starts nothing`() =
        runTest {
            val commands = refusing()
            val controller = asked(commands)
            trust.failNext(TrustError.CONFIG_MISSING)

            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()

            controller.shown().trust.shouldBeNull()
            controller.shown().problem shouldBe NewAgentProblem.TrustFailed(dir)
            controller.shown().busy shouldBe false
            controller.shown().form.prompt shouldBe "keep me"
            commands.attempts.size shouldBe 1
            started.shouldBeEmpty()
        }

    @Test
    fun `trust failure logs the category and not the directory`() =
        runTest {
            val controller = asked()
            trust.failNext(TrustError.CONFIG_INVALID)

            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()

            val warn = log.entriesAt(LogLevel.WARN).joinToString(" ") { "${it.message} ${it.fields.values}" }
            warn shouldContain "CONFIG_INVALID"
            warn shouldNotContain dir.toString()
        }

    @Test
    fun `after a trust failure submit asks again`() =
        runTest {
            val commands = refusing(2)
            val controller = asked(commands)
            trust.failNext(TrustError.WRITE_FAILED)
            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()

            submit(controller)

            controller.shown().trust shouldBe TrustStep(dir)
            controller.shown().problem.shouldBeNull()
            commands.attempts.size shouldBe 2
        }

    @Test
    fun `still untrusted after trust shows a hint and does not ask again`() =
        runTest {
            val commands = refusing(2)
            val controller = asked(commands)

            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()

            controller.shown().trust.shouldBeNull()
            controller.shown().problem shouldBe NewAgentProblem.TrustIneffective(dir)
            controller.shown().busy shouldBe false
            commands.attempts.size shouldBe 2
            trust.trusted.size shouldBe 1
            started.shouldBeEmpty()
        }

    @Test
    fun `a new submit after that asks again`() =
        runTest {
            val commands = refusing(3)
            val controller = asked(commands)
            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()

            submit(controller)

            controller.shown().trust shouldBe TrustStep(dir)
            controller.shown().problem.shouldBeNull()
            commands.attempts.size shouldBe 3
        }

    @Test
    fun `other start failures do not open the trust step`() =
        runTest {
            val controller = opened()
            controller.edit()
            fake.failNext(refused)

            submit(controller)

            controller.shown().trust.shouldBeNull()
            controller.shown().problem shouldBe NewAgentProblem.StartFailed(NewAgentRules.failureOf(refused))
        }

    @Test
    fun `logs never contain the directory or the input`() =
        runTest {
            val controller = opened(refusing(3))
            controller.onEvent(NewAgentEvent.Edit(NewAgentForm(QUAYSIDE.id, prompt = NewAgentWorld.SECRET_PROMPT)))
            submit(controller)
            controller.onEvent(NewAgentEvent.TrustDecline)
            submit(controller)
            trust.failNext(TrustError.WRITE_FAILED)
            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()
            submit(controller)
            controller.onEvent(NewAgentEvent.TrustAccept)
            runCurrent()

            val dump = log.dump()
            dump shouldNotContain dir.toString()
            dump shouldNotContain "/Users/test"
            dump shouldNotContain NewAgentWorld.SECRET_PROMPT
        }
}
