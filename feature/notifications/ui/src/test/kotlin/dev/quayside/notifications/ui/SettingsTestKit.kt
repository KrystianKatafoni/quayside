@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.quayside.notifications.ui

import dev.quayside.core.domain.Outcome
import dev.quayside.notifications.api.FakeNotificationSettingsRepo
import dev.quayside.notifications.api.NotificationSettings
import dev.quayside.notifications.api.NotificationSettingsError
import dev.quayside.notifications.api.NotificationSettingsRepo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

/** Runs [block] and then everything it scheduled. */
internal fun TestScope.act(block: () -> Unit) {
    block()
    runCurrent()
}

/**
 * A repo over [inner] that counts writes, can refuse them with [failure] and can hold them until [release]
 * completes (when [hold] is true).
 */
internal class ScriptedRepo(
    private val inner: FakeNotificationSettingsRepo,
    private val hold: Boolean = false,
) : NotificationSettingsRepo by inner {
    val release = CompletableDeferred<Unit>()
    var failure: NotificationSettingsError? = null
    var updates: Int = 0
        private set

    override suspend fun update(
        change: (NotificationSettings) -> NotificationSettings,
    ): Outcome<Unit, NotificationSettingsError> {
        updates++
        if (hold) release.await()
        val refused = failure
        return if (refused != null) Outcome.Err(refused) else inner.update(change)
    }
}
