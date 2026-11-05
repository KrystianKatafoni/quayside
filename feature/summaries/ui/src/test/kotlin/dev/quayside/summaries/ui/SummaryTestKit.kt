@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.summaries.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.summaries.api.FakeSummarySettingsRepo
import dev.quayside.summaries.api.SummarySettingsError
import dev.quayside.summaries.api.SummarySettingsRepo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/** Runs [block] and then everything it scheduled. */
internal fun TestScope.act(block: () -> Unit) {
    block()
    runCurrent()
}

/** A repo over [inner] that counts writes and can hold them until [release] completes (when [hold] is true). */
internal class ScriptedRepo(
    private val inner: FakeSummarySettingsRepo,
    private val hold: Boolean = false,
) : SummarySettingsRepo by inner {
    val release = CompletableDeferred<Unit>()
    var writes: Int = 0
        private set

    override suspend fun setUseModel(on: Boolean): Outcome<Unit, SummarySettingsError> {
        writes++
        if (hold) release.await()
        return inner.setUseModel(on)
    }
}
