package dev.quayside.summaries.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Behaviour every [SummarySettingsRepo] must have (ENG-T2). Runs on the fake and on the file store. */
public abstract class SummarySettingsRepoContract {
    /** A repository with nothing stored. */
    protected abstract fun create(): SummarySettingsRepo

    @Test
    public fun `fresh repository has the model off`(): Unit =
        runTest {
            val repo = create()

            repo.useModel.value shouldBe false
            repo.startedWithModel shouldBe false
        }

    @Test
    public fun `setUseModel publishes the new switch`(): Unit =
        runTest {
            val repo = create()

            repo.setUseModel(on = true) shouldBe Outcome.Ok(Unit)

            repo.useModel.value shouldBe true
        }

    @Test
    public fun `setUseModel can switch back off`(): Unit =
        runTest {
            val repo = create()
            repo.setUseModel(on = true)

            repo.setUseModel(on = false) shouldBe Outcome.Ok(Unit)

            repo.useModel.value shouldBe false
        }

    @Test
    public fun `a change does not alter what the app started with`(): Unit =
        runTest {
            val repo = create()

            repo.setUseModel(on = true)

            repo.startedWithModel shouldBe false
        }
}
