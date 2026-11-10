@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.missioncontrol.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.sessions.api.FakeSessionHistorySettings
import dev.quayside.sessions.api.HistoryAge
import dev.quayside.sessions.api.HistoryLimit
import dev.quayside.sessions.api.HistorySettingsError
import dev.quayside.sessions.api.SessionHistorySettings
import dev.quayside.sessions.api.SessionHistorySettingsRepo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The "Stare sesje z dysku" dialog (FR-P9): every choice saves at once, the dialog shows what is saved. */
class HistorySettingsControllerTest {
    private val repo = FakeSessionHistorySettings()

    private fun TestScope.start(source: SessionHistorySettingsRepo = repo): HistorySettingsController =
        HistorySettingsController(source, backgroundScope).also { runCurrent() }

    private fun TestScope.act(
        controller: HistorySettingsController,
        action: HistorySettingsController.() -> Unit,
    ) {
        controller.action()
        runCurrent()
    }

    @Test
    fun `the dialog is closed until it is opened and then shows the saved choices`() =
        runTest {
            val saved = FakeSessionHistorySettings(SessionHistorySettings(HistoryAge.DAYS_90, HistoryLimit.SESSIONS_50))
            val controller = start(saved)
            controller.state.value.shouldBeNull()
            controller.isOpen.value shouldBe false

            act(controller) { open() }

            controller.state.value shouldBe HistoryDialogState(HistoryAge.DAYS_90, HistoryLimit.SESSIONS_50)
            controller.isOpen.value shouldBe true
        }

    @Test
    fun `an age choice saves at once and keeps the dialog open`() =
        runTest {
            val controller = start()
            act(controller) { open() }

            act(controller) { chooseAge(HistoryAge.DAYS_7) }

            repo.settings.value.maxAge shouldBe HistoryAge.DAYS_7
            repo.updates shouldBe 1
            controller.state.value shouldBe HistoryDialogState(HistoryAge.DAYS_7, HistoryLimit.SESSIONS_200)
        }

    @Test
    fun `a limit choice saves at once and keeps the age`() =
        runTest {
            val controller = start()
            act(controller) { open() }
            act(controller) { chooseAge(HistoryAge.UNLIMITED) }

            act(controller) { chooseLimit(HistoryLimit.SESSIONS_1000) }

            repo.settings.value shouldBe SessionHistorySettings(HistoryAge.UNLIMITED, HistoryLimit.SESSIONS_1000)
            controller.state.value shouldBe HistoryDialogState(HistoryAge.UNLIMITED, HistoryLimit.SESSIONS_1000)
        }

    @Test
    fun `hidden sessions stay when a choice is saved`() =
        runTest {
            val hidden =
                setOf(
                    dev.quayside.core.domain
                        .SessionId("aaaa"),
                )
            val saved = FakeSessionHistorySettings(SessionHistorySettings(hidden = hidden))
            val controller = start(saved)
            act(controller) { open() }

            act(controller) { chooseAge(HistoryAge.DAYS_90) }

            saved.settings.value.hidden shouldBe hidden
        }

    @Test
    fun `a failed save keeps the previous choice and shows the error`() =
        runTest {
            val controller = start()
            act(controller) { open() }
            repo.failNext(HistorySettingsError.WRITE_FAILED)

            act(controller) { chooseAge(HistoryAge.DAYS_7) }

            repo.settings.value.maxAge shouldBe HistoryAge.DAYS_30
            controller.state.value shouldBe
                HistoryDialogState(HistoryAge.DAYS_30, HistoryLimit.SESSIONS_200, HistorySettingsError.WRITE_FAILED)
        }

    @Test
    fun `a good choice after a failed one clears the error`() =
        runTest {
            val controller = start()
            act(controller) { open() }
            repo.failNext(HistorySettingsError.NEWER_SCHEMA)
            act(controller) { chooseAge(HistoryAge.DAYS_7) }

            act(controller) { chooseLimit(HistoryLimit.SESSIONS_50) }

            controller.state.value shouldBe HistoryDialogState(HistoryAge.DAYS_30, HistoryLimit.SESSIONS_50)
        }

    @Test
    fun `a choice while the dialog is closed saves nothing`() =
        runTest {
            val controller = start()

            act(controller) { chooseAge(HistoryAge.DAYS_7) }
            act(controller) { chooseLimit(HistoryLimit.SESSIONS_50) }

            repo.updates shouldBe 0
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `reopening shows the saved choice after another was picked and closed`() =
        runTest {
            val controller = start()
            act(controller) { open() }
            act(controller) { chooseAge(HistoryAge.DAYS_7) }
            act(controller) { close() }
            controller.state.value.shouldBeNull()

            act(controller) { open() }

            controller.state.value shouldBe HistoryDialogState(HistoryAge.DAYS_7, HistoryLimit.SESSIONS_200)
        }

    @Test
    fun `a result that arrives after closing is dropped, also after reopening`() =
        runTest {
            val gated = GatedHistorySettings(repo)
            gated.failure = HistorySettingsError.WRITE_FAILED
            val controller = start(gated)
            act(controller) { open() }
            act(controller) { chooseAge(HistoryAge.DAYS_7) }
            act(controller) { close() }
            act(controller) { open() }

            gated.gate.complete(Unit)
            runCurrent()

            controller.state.value shouldBe HistoryDialogState(HistoryAge.DAYS_30, HistoryLimit.SESSIONS_200)
        }
}

/** A repo whose `update` waits for [gate] and then fails with [failure] (when set) or delegates to [inner]. */
private class GatedHistorySettings(
    private val inner: FakeSessionHistorySettings,
) : SessionHistorySettingsRepo by inner {
    val gate = CompletableDeferred<Unit>()
    var failure: HistorySettingsError? = null

    override suspend fun update(
        change: (SessionHistorySettings) -> SessionHistorySettings,
    ): Outcome<SessionHistorySettings, HistorySettingsError> {
        gate.await()
        return failure?.let { Outcome.Err(it) } ?: inner.update(change)
    }
}
