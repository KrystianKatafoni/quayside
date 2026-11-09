package dev.quayside.summaries.api

import dev.quayside.core.domain.Outcome
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** [SummarySettingsRepoContract] on the fake, plus the fake's own failure switch. */
class FakeSummarySettingsRepoContractTest : SummarySettingsRepoContract() {
    override fun create(): SummarySettingsRepo = FakeSummarySettingsRepo()

    @Test
    fun `the initial value is what the app started with`() {
        FakeSummarySettingsRepo(initial = true).startedWithModel shouldBe true
        FakeSummarySettingsRepo(initial = true).useModel.value shouldBe true
    }

    @Test
    fun `a failure publishes nothing and clears when unset`() =
        runTest {
            val repo = FakeSummarySettingsRepo()
            repo.failure = SummarySettingsError.WRITE_FAILED

            repo.setUseModel(on = true) shouldBe Outcome.Err(SummarySettingsError.WRITE_FAILED)
            repo.useModel.value shouldBe false

            repo.failure = null
            repo.setUseModel(on = true) shouldBe Outcome.Ok(Unit)
            repo.useModel.value shouldBe true
        }
}
