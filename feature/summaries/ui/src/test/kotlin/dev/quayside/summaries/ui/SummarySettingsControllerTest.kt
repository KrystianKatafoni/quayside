package dev.quayside.summaries.ui

import dev.quayside.summaries.api.FakeSummarySettingsRepo
import dev.quayside.summaries.api.SummarySettingsError
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** The logic of the summaries settings dialog over a fake repo (FR-S7, plan AM-606). */
class SummarySettingsControllerTest {
    private fun TestScope.controllerOver(repo: ScriptedRepo) = SummarySettingsController(repo, backgroundScope)

    private fun scripted(
        initial: Boolean = false,
        hold: Boolean = false,
    ) = ScriptedRepo(FakeSummarySettingsRepo(initial), hold)

    @Test
    fun `closed until opened`() =
        runTest {
            val controller = controllerOver(scripted())

            controller.state.value.shouldBeNull()
            controller.isOpen.value shouldBe false
        }

    @Test
    fun `opens with the saved switch and no restart note`() =
        runTest {
            val controller = controllerOver(scripted(initial = true))

            act { controller.open() }

            controller.state.value shouldBe SummaryDialogState(useModel = true, restartNeeded = false)
            controller.isOpen.value shouldBe true
        }

    @Test
    fun `a draft that differs from the started value asks for a restart`() =
        runTest {
            val controller = controllerOver(scripted(initial = false))
            act { controller.open() }

            act { controller.setUseModel(true) }

            controller.state.value shouldBe SummaryDialogState(useModel = true, restartNeeded = true)

            act { controller.setUseModel(false) }

            controller.state.value shouldBe SummaryDialogState(useModel = false, restartNeeded = false)
        }

    @Test
    fun `an edit changes the draft only, nothing is written`() =
        runTest {
            val repo = scripted()
            val controller = controllerOver(repo)
            act { controller.open() }

            act { controller.setUseModel(true) }

            repo.useModel.value shouldBe false
            repo.writes shouldBe 0
        }

    @Test
    fun `an edit while closed opens nothing`() =
        runTest {
            val controller = controllerOver(scripted())

            act { controller.setUseModel(true) }

            controller.state.value.shouldBeNull()
        }

    @Test
    fun `save writes the draft and closes`() =
        runTest {
            val repo = scripted()
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.setUseModel(true) }

            act { controller.save() }

            repo.useModel.value shouldBe true
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `a refused write keeps the dialog open with the error until the next edit`() =
        runTest {
            val inner = FakeSummarySettingsRepo()
            inner.failure = SummarySettingsError.WRITE_FAILED
            val controller = SummarySettingsController(ScriptedRepo(inner), backgroundScope)
            act { controller.open() }
            act { controller.setUseModel(true) }

            act { controller.save() }

            controller.state.value
                .shouldNotBeNull()
                .saveError shouldBe SummarySettingsError.WRITE_FAILED
            inner.useModel.value shouldBe false

            act { controller.setUseModel(false) }

            controller.state.value
                .shouldNotBeNull()
                .saveError
                .shouldBeNull()
        }

    @Test
    fun `a second save while the first runs writes once`() =
        runTest {
            val repo = scripted(hold = true)
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.setUseModel(true) }
            act { controller.save() }

            act { controller.save() }

            repo.writes shouldBe 1
            repo.release.complete(Unit)
            testScheduler.runCurrent()
            controller.state.value.shouldBeNull()
        }

    @Test
    fun `a result after cancel is dropped and does not close a reopened dialog`() =
        runTest {
            val repo = scripted(hold = true)
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.setUseModel(true) }
            act { controller.save() }
            act { controller.cancel() }
            act { controller.open() }

            repo.release.complete(Unit)
            testScheduler.runCurrent()

            controller.state.value.shouldNotBeNull()
        }

    @Test
    fun `cancel closes without writing`() =
        runTest {
            val repo = scripted()
            val controller = controllerOver(repo)
            act { controller.open() }
            act { controller.setUseModel(true) }

            act { controller.cancel() }

            controller.state.value.shouldBeNull()
            repo.writes shouldBe 0
        }

    @Test
    fun `a failed save can be tried again after the cause is gone`() =
        runTest {
            val inner = FakeSummarySettingsRepo()
            inner.failure = SummarySettingsError.WRITE_FAILED
            val controller = SummarySettingsController(ScriptedRepo(inner), backgroundScope)
            act { controller.open() }
            act { controller.setUseModel(true) }
            act { controller.save() }
            inner.failure = null

            act { controller.save() }

            inner.useModel.value shouldBe true
            controller.state.value.shouldBeNull()
        }
}
